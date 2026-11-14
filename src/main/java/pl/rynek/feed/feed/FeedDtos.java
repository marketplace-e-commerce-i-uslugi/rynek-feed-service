package pl.rynek.feed.feed;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.rynek.feed.events.EventService.EventType;
import tools.jackson.databind.JsonNode;

/** Kształty zgodne z rynek-frontend/src/app/core/api/models/feed.ts. */
public final class FeedDtos {

    private FeedDtos() {
    }

    public record Page<T>(List<T> items, String nextCursor) {
    }

    public record Chip(String id, String label) {
    }

    /** {@code offer} to {@code OfferCard} z search/catalog bez zmian. */
    public record Item(String kind, JsonNode offer) {
        public static Item of(JsonNode offer) {
            return new Item("offer", offer);
        }
    }

    /** {@code video} to {@code Video} z tiktok-integration bez zmian; {@code offer} to karta oferty filmu albo {@code null}. */
    public record ShortItem(JsonNode video, JsonNode offer) {
    }

    public record EventRequest(
            @NotNull(message = "Podaj rodzaj zdarzenia.") EventType type,
            UUID offerId,
            UUID videoId,
            @Min(value = 0, message = "Czas oglądania nie może być ujemny.")
            @Max(value = 86_400_000L, message = "Czas oglądania jest za długi.") Long valueMs) {
    }

    public record RecordEventsRequest(
            @NotNull(message = "Podaj listę zdarzeń.")
            @Size(max = 50, message = "Najwyżej 50 zdarzeń w jednym żądaniu.")
            List<@Valid @NotNull EventRequest> events) {
    }
}
