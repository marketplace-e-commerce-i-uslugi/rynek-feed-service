# rynek-feed-service

`feed-service` — strona główna, Shorts, „Następne”

> Status: **szkielet**. Wszystkie endpointy z tabeli istnieją w kontrolerach (`src/main/java/.../api/`) i zwracają `501 Not Implemented` (RFC 7807). Ciała żądań to puste rekordy w `api/dto/`.

## Uruchomienie

```bash
./mvnw spring-boot:run   # port 8086, zmienna PORT
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
