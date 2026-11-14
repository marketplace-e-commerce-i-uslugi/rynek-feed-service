package pl.rynek.feed.sync;

import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Klient HTTP do innego serwisu z krótkimi limitami czasu (feed nie ma czekać na wolny serwis). */
final class Downstream {

    private Downstream() {
    }

    static RestClient client(String baseUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }
}
