package pl.rynek.feed.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Kategorie, szczegóły oferty i oferty kanału z catalog-service. */
@Component
public class CatalogClient {

    private final RestClient http;

    public CatalogClient(@Value("${rynek.catalog.url}") String url) {
        this.http = Downstream.client(url);
    }

    public JsonNode categories() {
        return http.get().uri("/api/v1/categories").retrieve().body(JsonNode.class);
    }

    /** Szczegóły oferty; nieistniejąca albo nieopublikowana daje wyjątek 404 z catalog. */
    public JsonNode offer(String offerId) {
        return http.get().uri("/api/v1/offers/{id}", offerId).retrieve().body(JsonNode.class);
    }

    /** Karty ofert po id (opublikowane); brakujące i szkice są pomijane. */
    public JsonNode offersBatch(String ids) {
        return http.get().uri(uri -> uri.path("/api/v1/offers/batch").queryParam("ids", ids).build()).retrieve().body(JsonNode.class);
    }

    /** Strona najnowszych ofert kanału (karty): {@code items}, {@code nextCursor}. Limit najwyżej 50 (tyle przyjmuje catalog). */
    public JsonNode channelOffers(String channelId, int limit, String cursor) {
        return http.get().uri(uri -> {
            var b = uri.path("/api/v1/offers").queryParam("channelId", channelId).queryParam("sort", "newest").queryParam("limit", limit);
            if (cursor != null) b.queryParam("cursor", cursor);
            return b.build();
        }).retrieve().body(JsonNode.class);
    }
}
