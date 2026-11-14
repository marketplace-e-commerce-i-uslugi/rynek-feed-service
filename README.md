# rynek-feed-service

`feed-service` — strona główna, Shorts, „Następne”

> Status: **działa** (bez Kafki). Feed składa gotowe dane z search-service (oferty), catalog (kategorie, oferty kanału) i channel (obserwowane), zdarzenia użytkownika zapisuje w PostgreSQL (baza `feed`, Flyway, JWT z JWKS).

- `chips`: Wszystko, Dla Ciebie (po zalogowaniu), kategorie główne z catalog, Usługi, Okazje.
- `home?chip=`: `all` trafność, `for-you` najnowsze z obejrzanymi na koniec strony, `services` tylko usługi, `deals` od najtańszych (w ofertach nie ma ceny przed obniżką), inne `chip` to id kategorii. Oferty oznaczone „nie interesuje mnie” znikają zalogowanemu. Kursor to kursor search.
- `shorts`: oferty z filmem (`hasVideo`), bo tiktok-integration jest szkieletem: `video.id` to id oferty, okładka to miniatura, `embedUrl` puste, `viewCount` 0.
- `following`: najnowsze oferty z do 20 obserwowanych kanałów, na przemian po jednej z kanału (karty nie mają daty), kursor to przesunięcie na połączonej liście (z każdego kanału pobierane tyle, ile trzeba do końca strony). Filmów z kanałów jeszcze nie ma.
- `related`: inne oferty z kategorii oferty (404 dla nieistniejącej).
- `events`: `VIEW`, `TAP`, `WATCH_TIME`, `NOT_INTERESTED` (do 50 w żądaniu, 204); gość też może je wysłać, ale bez wpływu na jego feed.
- Gdy search nie działa, feed zwraca 503, a brak catalog tylko skraca chipy.

## Uruchomienie

```bash
./mvnw spring-boot:run   # port 8086, zmienna PORT; baza `feed` z rynek-infra/local
./mvnw verify            # testy
```

- Swagger UI: http://localhost:8086/swagger-ui.html
- Zdrowie: http://localhost:8086/actuator/health

## Zakres i endpointy

_Skopiowane z `rynek-docs/architektura/rynek-endpointy-mikroserwisy.md`. Od teraz to repozytorium jest właścicielem tej specyfikacji._

Ekrany: 2 Strona główna, Shorts, Obserwowane, karta oferty („Następne”). Serwis składa feed z gotowych danych (zdarzenia z catalog, tiktok, engagement), nie wywołuje ich synchronicznie dla każdego kafla.

| Metoda | Ścieżka | Opis | Dostęp |
|---|---|---|---|
| GET | `/api/v1/feed/home?chip=&cursor=` | Feed rekomendacji; `chip` = kategoria z paska chipów | pub (personalizacja po zalogowaniu) |
| GET | `/api/v1/feed/chips` | Chipy kategorii dla użytkownika (Wszystko, Dla Ciebie, kategorie, Usługi, Okazje) | pub |
| GET | `/api/v1/feed/shorts?cursor=` | Strumień pionowych filmów z powiązanymi ofertami | pub |
| GET | `/api/v1/feed/following?cursor=` | Nowe oferty i filmy z obserwowanych kanałów | user |
| GET | `/api/v1/feed/related?offerId=&cursor=` | „Następne” obok karty oferty | pub |
| POST | `/api/v1/feed/events` | Zdarzenia dla rekomendacji: wyświetlenie, tapnięcie, czas oglądania, „nie interesuje mnie” | pub |

## Zdarzenia Kafka

Wiersze z §17, w których występuje `feed` (publikuje lub słucha).

| Zdarzenie | Publikuje | Słuchają |
|---|---|---|
| `channel.created`, `channel.updated` | channel | search, feed |
| `channel.followed`, `channel.unfollowed` | channel | feed, notification, analytics |
| `offer.published`, `offer.updated`, `offer.unpublished` | catalog | search, feed, cart, analytics |
| `video.synced`, `video.attached`, `video.removed` | tiktok-integration | catalog, feed, search |

## Dane i zależności

- **Magazyn danych:** PostgreSQL lub Redis (zbudowane feedy)
- **Zależności:** zdarzenia z catalog, tiktok-integration, engagement, channel
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 3. `search-service`, `feed-service` — odkrywanie ofert.
