package pl.rynek.feed.events;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Zdarzenie użytkownika (albo gościa, wtedy bez {@code userId}) dla rekomendacji. */
@Entity
@Table(name = "feed_events")
public class FeedEvent {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    private String type;

    @Column(name = "offer_id")
    private UUID offerId;

    @Column(name = "video_id")
    private UUID videoId;

    @Column(name = "value_ms")
    private Long valueMs;

    @Column(name = "created_at")
    private Instant createdAt;

    protected FeedEvent() {
    }

    public FeedEvent(UUID userId, String type, UUID offerId, UUID videoId, Long valueMs, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.offerId = offerId;
        this.videoId = videoId;
        this.valueMs = valueMs;
        this.createdAt = createdAt;
    }
}
