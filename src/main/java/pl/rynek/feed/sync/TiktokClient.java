package pl.rynek.feed.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Pionowe filmy z tiktok-integration-service ({@code GET /api/v1/videos/shorts}); filmy przechodzą dalej bez zmian. */
@Component
public class TiktokClient {

    private final RestClient http;

    public TiktokClient(@Value("${rynek.tiktok.url}") String url) {
        this.http = Downstream.client(url);
    }

    /** Strona filmów: {@code items} (Video), {@code nextCursor}. */
    public JsonNode shorts(String cursor) {
        return http.get().uri(uri -> {
            var b = uri.path("/api/v1/videos/shorts");
            if (cursor != null) b.queryParam("cursor", cursor);
            return b.build();
        }).retrieve().body(JsonNode.class);
    }
}
