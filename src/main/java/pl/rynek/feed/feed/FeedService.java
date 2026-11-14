package pl.rynek.feed.feed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.feed.events.EventService;
import pl.rynek.feed.feed.FeedDtos.Chip;
import pl.rynek.feed.feed.FeedDtos.Item;
import pl.rynek.feed.feed.FeedDtos.Page;
import pl.rynek.feed.feed.FeedDtos.ShortItem;
import pl.rynek.feed.feed.FeedDtos.Video;
import pl.rynek.feed.sync.CatalogClient;
import pl.rynek.feed.sync.ChannelClient;
import pl.rynek.feed.sync.SearchClient;
import tools.jackson.databind.JsonNode;

/**
 * Feed składany z gotowych danych: wyniki z search-service (indeks ofert), kategorie i oferty kanału z catalog, obserwowane
 * z channel. Zamiast zdarzeń Kafki (jeszcze ich nie ma) czyta synchronicznie, ale raz na stronę, nie na kafel.
 */
@Service
public class FeedService {

    private final SearchClient search;
    private final CatalogClient catalog;
    private final ChannelClient channels;
    private final EventService events;
    private final int pageSize;
    private final int followingMaxChannels;

    public FeedService(SearchClient search, CatalogClient catalog, ChannelClient channels, EventService events,
                       @Value("${rynek.feed.page-size}") int pageSize,
                       @Value("${rynek.feed.following-max-channels}") int followingMaxChannels) {
        this.search = search;
        this.catalog = catalog;
        this.channels = channels;
        this.events = events;
        this.pageSize = pageSize;
        this.followingMaxChannels = followingMaxChannels;
    }

    /** Wszystko, Dla Ciebie (po zalogowaniu), kategorie główne, Usługi, Okazje. Kategorie pomijamy, gdy catalog nie odpowiada. */
    public List<Chip> chips(Optional<UUID> viewer) {
        List<Chip> chips = new ArrayList<>();
        chips.add(new Chip("all", "Wszystko"));
        if (viewer.isPresent()) {
            chips.add(new Chip("for-you", "Dla Ciebie"));
        }
        try {
            for (JsonNode c : catalog.categories()) {
                chips.add(new Chip(c.path("id").asString(), c.path("name").asString()));
            }
        } catch (RuntimeException e) {
            // Brak kategorii nie psuje reszty chipów.
        }
        chips.add(new Chip("services", "Usługi"));
        chips.add(new Chip("deals", "Okazje"));
        return chips;
    }

    /**
     * Chipy: {@code all} (trafność), {@code for-you} (najnowsze, obejrzane na koniec strony), {@code services}, {@code deals}
     * (od najtańszych), inna wartość to id kategorii. Oferty oznaczone „nie interesuje mnie” znikają.
     */
    public Page<Item> home(String chip, String cursor, Optional<UUID> viewer) {
        String id = chip == null || chip.isBlank() ? "all" : chip;
        String category = null;
        String type = null;
        String sort = null;
        switch (id) {
            case "all" -> { }
            case "for-you" -> sort = "newest";
            case "services" -> type = "SERVICE";
            case "deals" -> sort = "price_asc";
            default -> category = id;
        }
        JsonNode result = search.offers(category, type, sort, null, cursor);
        Set<UUID> hidden = events.hidden(viewer);
        List<JsonNode> cards = new ArrayList<>();
        for (JsonNode card : result.path("items")) {
            if (!hidden.contains(idOf(card))) {
                cards.add(card);
            }
        }
        if (id.equals("for-you")) {
            Set<UUID> seen = events.seen(viewer);
            cards.sort(Comparator.comparingInt(c -> seen.contains(idOf(c)) ? 1 : 0));
        }
        return new Page<>(cards.stream().map(Item::of).toList(), cursorOf(result));
    }

    /**
     * Najnowsze oferty z obserwowanych kanałów, na przemian po jednej z kanału (karty nie mają daty). Kursor to przesunięcie na
     * połączonej liście; z każdego kanału pobieramy tyle ofert, ile trzeba do końca żądanej strony (strony po 50 z catalog).
     */
    public Page<Item> following(String bearerToken, String cursor) {
        int offset = offset(cursor);
        int want = offset + pageSize;
        List<List<JsonNode>> perChannel = new ArrayList<>();
        boolean channelHasMore = false;
        int taken = 0;
        for (JsonNode ch : channels.following(bearerToken)) {
            if (taken++ >= followingMaxChannels) {
                break;
            }
            ChannelOffers fetched = channelOffers(ch.path("id").asString(), want);
            channelHasMore |= fetched.more();
            if (!fetched.items().isEmpty()) {
                perChannel.add(fetched.items());
            }
        }
        List<JsonNode> merged = new ArrayList<>();
        for (int round = 0; ; round++) {
            boolean any = false;
            for (List<JsonNode> offers : perChannel) {
                if (round < offers.size()) {
                    merged.add(offers.get(round));
                    any = true;
                }
            }
            if (!any) {
                break;
            }
        }
        int end = Math.min(merged.size(), offset + pageSize);
        List<Item> items = offset >= merged.size() ? List.of() : merged.subList(offset, end).stream().map(Item::of).toList();
        return new Page<>(items, end < merged.size() || channelHasMore ? String.valueOf(end) : null);
    }

    /** Oferty z filmem jako Shorts. Filmów jeszcze nie serwuje tiktok-integration, więc film opisuje oferta (okładka = miniatura). */
    public Page<ShortItem> shorts(String cursor) {
        JsonNode result = search.offers(null, null, "newest", true, cursor);
        List<ShortItem> items = new ArrayList<>();
        for (JsonNode card : result.path("items")) {
            String id = card.path("id").asString();
            Video video = new Video(id, card.path("title").asString(), null, card.path("thumbnailUrl").asString(null),
                    card.path("videoDurationSec").asInt(0), true, 0, false, id, card.path("channel"), card.path("tone").asInt(1));
            items.add(new ShortItem(video, card));
        }
        return new Page<>(items, cursorOf(result));
    }

    /** „Następne” obok oferty: inne oferty z jej kategorii. Nieistniejąca oferta daje 404. */
    public Page<JsonNode> related(String offerId, String cursor) {
        UUID id;
        try {
            id = UUID.fromString(offerId == null ? "" : offerId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj poprawny identyfikator oferty (offerId).");
        }
        String category = catalog.offer(id.toString()).path("categoryId").asString(null);
        JsonNode result = search.offers(category, null, category == null ? "newest" : null, null, cursor);
        List<JsonNode> cards = new ArrayList<>();
        for (JsonNode card : result.path("items")) {
            if (!id.equals(idOf(card))) {
                cards.add(card);
            }
        }
        return new Page<>(cards, cursorOf(result));
    }

    /** Najnowsze oferty kanału, najwyżej {@code want}; {@code more}, gdy kanał ma jeszcze starsze. */
    private ChannelOffers channelOffers(String channelId, int want) {
        List<JsonNode> offers = new ArrayList<>();
        String cursor = null;
        try {
            while (true) {
                JsonNode page = catalog.channelOffers(channelId, Math.min(50, want - offers.size()), cursor);
                page.path("items").forEach(offers::add);
                JsonNode next = page.path("nextCursor");
                cursor = next.isNull() || next.isMissingNode() ? null : next.asString();
                if (cursor == null || offers.size() >= want) {
                    return new ChannelOffers(offers, cursor != null);
                }
            }
        } catch (RuntimeException e) {
            // Jeden kanał, którego catalog nie umie zwrócić, nie psuje całej listy.
            return new ChannelOffers(offers, false);
        }
    }

    private record ChannelOffers(List<JsonNode> items, boolean more) {
    }

    private static UUID idOf(JsonNode card) {
        try {
            return UUID.fromString(card.path("id").asString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String cursorOf(JsonNode result) {
        JsonNode next = result.path("nextCursor");
        return next.isNull() || next.isMissingNode() ? null : next.asString();
    }

    private static int offset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(cursor);
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            // niżej: 400
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy kursor.");
    }
}
