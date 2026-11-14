package pl.rynek.feed.events;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeedEventRepository extends JpaRepository<FeedEvent, UUID> {

    /** Oferty danego typu zdarzenia u użytkownika (najnowsze najpierw, najwyżej {@code limit}). */
    @Query(value = "select offer_id from (select offer_id, max(created_at) as last from feed_events where user_id = :userId and type = :type "
            + "and offer_id is not null group by offer_id) t order by last desc limit :limit", nativeQuery = true)
    List<UUID> offersOf(UUID userId, String type, int limit);
}
