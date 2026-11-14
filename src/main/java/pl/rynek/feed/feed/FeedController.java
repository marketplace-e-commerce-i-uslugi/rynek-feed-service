package pl.rynek.feed.feed;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.feed.config.Viewer;
import pl.rynek.feed.events.EventService;
import pl.rynek.feed.events.EventService.EventInput;
import pl.rynek.feed.feed.FeedDtos.Chip;
import pl.rynek.feed.feed.FeedDtos.Item;
import pl.rynek.feed.feed.FeedDtos.Page;
import pl.rynek.feed.feed.FeedDtos.RecordEventsRequest;
import pl.rynek.feed.feed.FeedDtos.ShortItem;
import tools.jackson.databind.JsonNode;

@RestController
public class FeedController {

    private final FeedService feed;
    private final EventService events;
    private final Viewer viewer;

    public FeedController(FeedService feed, EventService events, Viewer viewer) {
        this.feed = feed;
        this.events = events;
        this.viewer = viewer;
    }

    /**
     * Feed rekomendacji; chip = kategoria z paska chipów. Dostęp: pub (personalizacja po zalogowaniu).
     */
    @GetMapping("/api/v1/feed/home")
    public Page<Item> getHomeFeed(HttpServletRequest request, @RequestParam(required = false) String chip,
                                  @RequestParam(required = false) String cursor) {
        return feed.home(chip, cursor, viewer.of(request));
    }

    /**
     * Chipy kategorii dla użytkownika (Wszystko, Dla Ciebie, kategorie, Usługi, Okazje). Dostęp: pub.
     */
    @GetMapping("/api/v1/feed/chips")
    public List<Chip> getChips(HttpServletRequest request) {
        return feed.chips(viewer.of(request));
    }

    /**
     * Strumień pionowych filmów z powiązanymi ofertami. Dostęp: pub.
     */
    @GetMapping("/api/v1/feed/shorts")
    public Page<ShortItem> getShortsFeed(@RequestParam(required = false) String cursor) {
        return feed.shorts(cursor);
    }

    /**
     * Nowe oferty i filmy z obserwowanych kanałów. Dostęp: user.
     */
    @GetMapping("/api/v1/feed/following")
    public Page<Item> getFollowingFeed(@AuthenticationPrincipal Jwt token, @RequestParam(required = false) String cursor) {
        return feed.following(token.getTokenValue(), cursor);
    }

    /**
     * „Następne” obok karty oferty. Dostęp: pub.
     */
    @GetMapping("/api/v1/feed/related")
    public Page<JsonNode> getRelatedOffers(@RequestParam(required = false) String offerId, @RequestParam(required = false) String cursor) {
        return feed.related(offerId, cursor);
    }

    /**
     * Zdarzenia dla rekomendacji: wyświetlenie, tapnięcie, czas oglądania, „nie interesuje mnie”. Dostęp: pub.
     */
    @PostMapping("/api/v1/feed/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordEvents(HttpServletRequest request, @Valid @RequestBody RecordEventsRequest body) {
        events.record(viewer.of(request), body.events().stream()
                .map(e -> new EventInput(e.type(), e.offerId(), e.videoId(), e.valueMs())).toList());
    }
}
