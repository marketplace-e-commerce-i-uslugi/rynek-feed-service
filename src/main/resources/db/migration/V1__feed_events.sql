-- Zdarzenia dla rekomendacji (POST /feed/events). user_id puste dla gościa. Używane do pomijania ofert „nie interesuje mnie”
-- i spychania na koniec tych, które użytkownik już widział (chip „Dla Ciebie”).
CREATE TABLE feed_events (
    id         uuid PRIMARY KEY,
    user_id    uuid,
    type       varchar(20) NOT NULL,
    offer_id   uuid,
    video_id   uuid,
    value_ms   bigint,
    created_at timestamptz NOT NULL
);

CREATE INDEX feed_events_user_idx ON feed_events (user_id, created_at DESC) WHERE user_id IS NOT NULL;
