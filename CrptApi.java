package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final Semaphore semaphore;
    private final int requestLimit;
    private final TimeUnit timeUnit;
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        semaphore = new Semaphore(requestLimit, true);

        Thread scheduler = new Thread(() -> {
            while (true){
                try {
                    Thread.sleep(this.timeUnit.toMillis(1));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                semaphore.drainPermits();
                semaphore.release(this.requestLimit);
            }
        });
        scheduler.setDaemon(true);
        scheduler.start();
    }

    public void request(Api api, Object document, String signature) throws IOException, URISyntaxException, InterruptedException {
        semaphore.acquireUninterruptibly();
        api.postDocument(document, signature);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DocumentWithSignature {
        Object document;
        String signature;
    }
    public static class Api {

        public static final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

        public void postDocument(Object document, String signature) throws IOException, URISyntaxException, InterruptedException {

            DocumentWithSignature documentWithSignature = new DocumentWithSignature(document, signature);
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(documentWithSignature);
            System.out.println(json);
            HttpRequest request = HttpRequest.newBuilder(new URI(URL))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.statusCode());
            System.out.println(response.body());
        }
    }


    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        CrptApi apiLimiter = new CrptApi(TimeUnit.SECONDS, 5);
        Api api = new Api();
        apiLimiter.request(api, "actualDocument", "actualSignature");
    }
}
