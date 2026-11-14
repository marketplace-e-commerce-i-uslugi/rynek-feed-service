package pl.rynek.feed.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Oferty z search-service ({@code GET /api/v1/search/offers}); karty przechodzą dalej bez zmian. */
@Component
public class SearchClient {

    private final RestClient http;

    public SearchClient(@Value("${rynek.search.url}") String url) {
        this.http = Downstream.client(url);
    }

    /** Strona wyników: {@code items}, {@code nextCursor}, {@code total}. Puste parametry są pomijane. */
    public JsonNode offers(String category, String type, String sort, Boolean hasVideo, String cursor) {
        return http.get().uri(uri -> {
            var b = uri.path("/api/v1/search/offers");
            if (category != null) b.queryParam("category", category);
            if (type != null) b.queryParam("type", type);
            if (sort != null) b.queryParam("sort", sort);
            if (hasVideo != null) b.queryParam("hasVideo", hasVideo);
            if (cursor != null) b.queryParam("cursor", cursor);
            return b.build();
        }).retrieve().body(JsonNode.class);
    }
}
