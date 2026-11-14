package pl.rynek.feed.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import pl.rynek.feed.TestcontainersConfig;
import pl.rynek.feed.sync.CatalogClient;
import pl.rynek.feed.sync.ChannelClient;
import pl.rynek.feed.sync.SearchClient;
import pl.rynek.feed.sync.TiktokClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Feed na prawdziwym PostgreSQL; search, catalog i channel to atrapy zwracające gotowe JSON-y. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class FeedFlowTests {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean SearchClient search;
    @MockitoBean CatalogClient catalog;
    @MockitoBean ChannelClient channels;
    @MockitoBean TiktokClient tiktok;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode(anyString())).willAnswer(call -> {
            String token = call.getArgument(0);
            if (!token.startsWith("user-")) {
                throw new BadJwtException("Nieważny token");
            }
            return Jwt.withTokenValue(token).header("alg", "RS256").subject(token.substring(5))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        });
        given(search.offers(any(), any(), any(), any(), any())).willReturn(page(null, A, B, C));
        given(catalog.categories()).willReturn(json.readTree("[{\"id\":\"elektronika\",\"name\":\"Elektronika\"},{\"id\":\"dom\",\"name\":\"Dom\"}]"));
    }

    @Test
    void chipsMajaKolejnoscZeReadme_ADlaCiebieTylkoPoZalogowaniu() throws Exception {
        mvc.perform(get("/api/v1/feed/chips")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("all", "elektronika", "dom", "services", "deals")));
        mvc.perform(get("/api/v1/feed/chips").header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(jsonPath("$[*].id", contains("all", "for-you", "elektronika", "dom", "services", "deals")));
    }

    @Test
    void chipsDzialajaBezKategoriiGdyCatalogNieOdpowiada() throws Exception {
        given(catalog.categories()).willThrow(new ResourceAccessException("down"));
        mvc.perform(get("/api/v1/feed/chips")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("all", "services", "deals")));
    }

    @Test
    void homeMapujeChipNaFiltrySearchIPrzekazujeKursor() throws Exception {
        mvc.perform(get("/api/v1/feed/home")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3))).andExpect(jsonPath("$.items[0].kind", is("offer")))
                .andExpect(jsonPath("$.items[0].offer.id", is(A.toString()))).andExpect(jsonPath("$.nextCursor", nullValue()));
        mvc.perform(get("/api/v1/feed/home").param("chip", "services").param("cursor", "abc")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/feed/home").param("chip", "deals")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/feed/home").param("chip", "elektronika")).andExpect(status().isOk());
        org.mockito.Mockito.verify(search).offers(null, "SERVICE", null, null, "abc");
        org.mockito.Mockito.verify(search).offers(null, null, "price_asc", null, null);
        org.mockito.Mockito.verify(search).offers("elektronika", null, null, null, null);
    }

    @Test
    void nieInteresujeMnieUkrywaOferteTylkoZalogowanemu_ObejrzaneIdaNaKoniecDlaCiebie() throws Exception {
        UUID user = UUID.randomUUID();
        String auth = bearer(user);
        mvc.perform(post("/api/v1/feed/events").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[{\"type\":\"NOT_INTERESTED\",\"offerId\":\"" + B + "\"},{\"type\":\"VIEW\",\"offerId\":\"" + A + "\"}]}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/feed/home").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.items[*].offer.id", contains(A.toString(), C.toString())));
        mvc.perform(get("/api/v1/feed/home").param("chip", "for-you").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.items[*].offer.id", contains(C.toString(), A.toString())));
        mvc.perform(get("/api/v1/feed/home")).andExpect(jsonPath("$.items", hasSize(3)));
    }

    @Test
    void ukryteOfertyDopelniajaStroneKolejnaPorcjaZSearch() throws Exception {
        UUID user = UUID.randomUUID();
        String auth = bearer(user);
        mvc.perform(post("/api/v1/feed/events").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[{\"type\":\"NOT_INTERESTED\",\"offerId\":\"" + A + "\"}]}")).andExpect(status().isNoContent());
        given(search.offers(any(), any(), any(), any(), eq((String) null))).willReturn(page("p2", A, B));
        given(search.offers(any(), any(), any(), any(), eq("p2"))).willReturn(page("p3", C));
        given(search.offers(any(), any(), any(), any(), eq("p3"))).willReturn(page(null));
        // Page-size w testach to 20, więc po ukryciu A dobieramy p2 i p3, aż skończą się oferty.
        mvc.perform(get("/api/v1/feed/home").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.items[*].offer.id", contains(B.toString(), C.toString())))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void zdarzeniaGoscia_walidacja() throws Exception {
        mvc.perform(post("/api/v1/feed/events").contentType(MediaType.APPLICATION_JSON).content("{\"events\":[{\"type\":\"TAP\",\"offerId\":\"" + A + "\"}]}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/feed/events").contentType(MediaType.APPLICATION_JSON).content("{\"events\":[]}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/feed/events").contentType(MediaType.APPLICATION_JSON).content("{\"events\":[{\"type\":\"LAJK\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/feed/events").contentType(MediaType.APPLICATION_JSON).content("{\"events\":[{\"type\":\"WATCH_TIME\",\"valueMs\":-5}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", is("Czas oglądania nie może być ujemny.")));
        mvc.perform(post("/api/v1/feed/events").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void shortsToFilmyZTiktokZKartamiOfert_filmBezOpublikowanejOfertyNieMaKarty() throws Exception {
        given(tiktok.shorts(null)).willReturn(json.readTree("{\"items\":["
                + "{\"id\":\"v1\",\"title\":\"Film A\",\"embedUrl\":\"/api/v1/tiktok/sandbox/player/x\",\"durationSec\":24,\"vertical\":true,\"viewCount\":5,\"offerId\":\"" + A + "\"},"
                + "{\"id\":\"v2\",\"title\":\"Film B\",\"vertical\":true,\"viewCount\":0,\"offerId\":\"" + B + "\"},"
                + "{\"id\":\"v3\",\"title\":\"Film C\",\"vertical\":true,\"viewCount\":0,\"offerId\":null}],\"nextCursor\":\"8\"}"));
        // Karty zwraca tylko opublikowana oferta A; B (szkic albo wstrzymana) nie ma karty.
        given(catalog.offersBatch(A + "," + B)).willReturn(json.readTree("[{\"id\":\"" + A + "\",\"title\":\"Oferta A\"}]"));
        mvc.perform(get("/api/v1/feed/shorts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].video.id", is("v1")))
                .andExpect(jsonPath("$.items[0].video.embedUrl", is("/api/v1/tiktok/sandbox/player/x")))
                .andExpect(jsonPath("$.items[0].offer.id", is(A.toString())))
                .andExpect(jsonPath("$.items[1].video.id", is("v2")))
                .andExpect(jsonPath("$.items[1].offer", nullValue()))
                .andExpect(jsonPath("$.items[2].offer", nullValue()))
                .andExpect(jsonPath("$.nextCursor", is("8")));
        given(tiktok.shorts("8")).willReturn(json.readTree("{\"items\":[],\"nextCursor\":null}"));
        mvc.perform(get("/api/v1/feed/shorts").param("cursor", "8")).andExpect(status().isOk());
        org.mockito.Mockito.verify(tiktok).shorts("8");
    }

    @Test
    void shortsBezFilmowToPustaLista_bezPytaniaOKarty() throws Exception {
        given(tiktok.shorts(null)).willReturn(json.readTree("{\"items\":[],\"nextCursor\":null}"));
        mvc.perform(get("/api/v1/feed/shorts")).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.nextCursor", nullValue()));
        org.mockito.Mockito.verify(catalog, org.mockito.Mockito.never()).offersBatch(anyString());
    }

    @Test
    void nastepnePomijajaOferteIUzywajaJejKategorii() throws Exception {
        given(catalog.offer(A.toString())).willReturn(json.readTree("{\"id\":\"" + A + "\",\"categoryId\":\"elektronika-rtv\"}"));
        given(search.offers(eq("elektronika-rtv"), any(), any(), any(), any())).willReturn(page(null, A, B));
        mvc.perform(get("/api/v1/feed/related").param("offerId", A.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(B.toString())));
    }

    @Test
    void nastepne_bledy() throws Exception {
        mvc.perform(get("/api/v1/feed/related")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/feed/related").param("offerId", "x")).andExpect(status().isBadRequest());
        given(catalog.offer(C.toString())).willThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));
        mvc.perform(get("/api/v1/feed/related").param("offerId", C.toString())).andExpect(status().isNotFound());
    }

    @Test
    void niedzialajacySearchDaje503() throws Exception {
        given(search.offers(any(), any(), any(), any(), any())).willThrow(new ResourceAccessException("down"));
        mvc.perform(get("/api/v1/feed/home")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail", is("Dane chwilowo niedostępne. Spróbuj ponownie za chwilę.")));
    }

    @Test
    void obserwowaneWymagajaLogowaniaILaczaOfertyKanalowNaPrzemian() throws Exception {
        mvc.perform(get("/api/v1/feed/following")).andExpect(status().isUnauthorized());
        UUID user = UUID.randomUUID();
        given(channels.following("user-" + user)).willReturn(json.readTree("[{\"id\":\"k1\"},{\"id\":\"k2\"},{\"id\":\"k3\"}]"));
        given(catalog.channelOffers(eq("k1"), org.mockito.ArgumentMatchers.anyInt(), any())).willReturn(page(null, A, B));
        given(catalog.channelOffers(eq("k2"), org.mockito.ArgumentMatchers.anyInt(), any())).willReturn(page(null, C));
        given(catalog.channelOffers(eq("k3"), org.mockito.ArgumentMatchers.anyInt(), any())).willThrow(new ResourceAccessException("down"));
        mvc.perform(get("/api/v1/feed/following").header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].offer.id", contains(A.toString(), C.toString(), B.toString())))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
        mvc.perform(get("/api/v1/feed/following").param("cursor", "zly").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void obserwowaneStronicujaOfertyJednegoKanalu() throws Exception {
        UUID user = UUID.randomUUID();
        UUID[] ids = new UUID[25];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = UUID.randomUUID();
        }
        given(channels.following("user-" + user)).willReturn(json.readTree("[{\"id\":\"k1\"}]"));
        // catalog oddaje strony po najwyżej 50: tu 25 ofert, a limit zależy od żądania.
        given(catalog.channelOffers(eq("k1"), org.mockito.ArgumentMatchers.anyInt(), any())).willAnswer(call -> {
            int limit = call.getArgument(1);
            return page(limit < ids.length ? "dalej" : null, java.util.Arrays.copyOf(ids, Math.min(limit, ids.length)));
        });
        mvc.perform(get("/api/v1/feed/following").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.items", hasSize(20))).andExpect(jsonPath("$.nextCursor", is("20")));
        mvc.perform(get("/api/v1/feed/following").param("cursor", "20").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.items", hasSize(5))).andExpect(jsonPath("$.items[0].offer.id", is(ids[20].toString())))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    private JsonNode page(String nextCursor, UUID... offers) {
        StringBuilder items = new StringBuilder();
        for (UUID id : offers) {
            items.append(items.isEmpty() ? "" : ",").append("{\"id\":\"").append(id).append("\",\"title\":\"Oferta ").append(id)
                    .append("\",\"thumbnailUrl\":\"http://x/").append(id).append("\",\"tone\":2,\"videoDurationSec\":30,\"channel\":{\"id\":\"k1\",\"name\":\"Sklep\"}}");
        }
        return json.readTree("{\"items\":[" + items + "],\"nextCursor\":" + (nextCursor == null ? "null" : "\"" + nextCursor + "\"") + ",\"total\":" + offers.length + "}");
    }

    private static String bearer(UUID user) {
        return "Bearer user-" + user;
    }
}
