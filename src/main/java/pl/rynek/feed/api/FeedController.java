package pl.rynek.feed.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.feed.api.dto.RecordEventsRequest;

import static pl.rynek.feed.common.NotImplemented.notImplemented;

@RestController
public class FeedController {

    /**
     * Feed rekomendacji; chip = kategoria z paska chipów. Dostęp: pub (personalizacja po zalogowaniu).
     */
    @GetMapping("/api/v1/feed/home")
    public void getHomeFeed(@RequestParam(required = false) String chip, @RequestParam(required = false) String cursor) {
        throw notImplemented();
    }

    /**
     * Chipy kategorii dla użytkownika (Wszystko, Dla Ciebie, kategorie, Usługi, Okazje). Dostęp: pub.
     */
    @GetMapping("/api/v1/feed/chips")
    public void getChips() {
        throw notImplemented();
    }

    /**
     * Strumień pionowych filmów z powiązanymi ofertami. Dostęp: pub.
     */
    @GetMapping("/api/v1/feed/shorts")
    public void getShortsFeed(@RequestParam(required = false) String cursor) {
        throw notImplemented();
    }

    /**
     * Nowe oferty i filmy z obserwowanych kanałów. Dostęp: user.
     */
    @GetMapping("/api/v1/feed/following")
    public void getFollowingFeed(@RequestParam(required = false) String cursor) {
        throw notImplemented();
    }

    /**
     * „Następne” obok karty oferty. Dostęp: pub.
     */
    @GetMapping("/api/v1/feed/related")
    public void getRelatedOffers(@RequestParam(required = false) String offerId, @RequestParam(required = false) String cursor) {
        throw notImplemented();
    }

    /**
     * Zdarzenia dla rekomendacji: wyświetlenie, tapnięcie, czas oglądania, „nie interesuje mnie”. Dostęp: pub.
     */
    @PostMapping("/api/v1/feed/events")
    public void recordEvents(@RequestBody RecordEventsRequest request) {
        throw notImplemented();
    }
}
