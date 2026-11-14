package pl.rynek.feed.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Obserwowane kanały użytkownika z channel-service; token użytkownika jedzie dalej (endpoint wymaga logowania). */
@Component
public class ChannelClient {

    private final RestClient http;

    public ChannelClient(@Value("${rynek.channel.url}") String url) {
        this.http = Downstream.client(url);
    }

    public JsonNode following(String bearerToken) {
        return http.get().uri("/api/v1/users/me/following").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve().body(JsonNode.class);
    }
}
