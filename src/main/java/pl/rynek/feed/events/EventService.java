package pl.rynek.feed.events;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private static final int REMEMBERED = 500;

    private final FeedEventRepository events;
    private final Clock clock;

    public EventService(FeedEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void record(Optional<UUID> user, List<EventInput> inputs) {
        events.saveAll(inputs.stream()
                .map(e -> new FeedEvent(user.orElse(null), e.type().name(), e.offerId(), e.videoId(), e.valueMs(), clock.instant()))
                .toList());
    }

    /** Oferty oznaczone „nie interesuje mnie”; gość nic nie ukrywa (jego zdarzenia nie mają właściciela). */
    @Transactional(readOnly = true)
    public Set<UUID> hidden(Optional<UUID> user) {
        return user.<Set<UUID>>map(u -> new HashSet<>(events.offersOf(u, EventType.NOT_INTERESTED.name(), REMEMBERED))).orElseGet(Set::of);
    }

    /** Oferty, które użytkownik już widział albo otwierał. */
    @Transactional(readOnly = true)
    public Set<UUID> seen(Optional<UUID> user) {
        return user.<Set<UUID>>map(u -> {
            Set<UUID> seen = new HashSet<>(events.offersOf(u, EventType.VIEW.name(), REMEMBERED));
            seen.addAll(events.offersOf(u, EventType.TAP.name(), REMEMBERED));
            return seen;
        }).orElseGet(Set::of);
    }

    public enum EventType { VIEW, TAP, WATCH_TIME, NOT_INTERESTED }

    public record EventInput(EventType type, UUID offerId, UUID videoId, Long valueMs) {
    }
}
