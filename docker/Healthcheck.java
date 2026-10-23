import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// The app secures every API: 401 confirms Spring's security chain is serving requests.
class Healthcheck {
    public static void main(String[] args) {
        try {
            var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                    .send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/api/investments"))
                            .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.discarding());
            System.exit(response.statusCode() == 401 ? 0 : 1);
        } catch (Exception ex) {
            System.exit(1);
        }
    }
}
