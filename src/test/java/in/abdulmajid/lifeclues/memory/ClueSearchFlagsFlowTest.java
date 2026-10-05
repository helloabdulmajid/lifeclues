package in.abdulmajid.lifeclues.memory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import in.abdulmajid.lifeclues.testing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Phase 3–6 coverage the original suite never had: flags (favorite/pin), the
 * clue model (mood + category/people/place), the Phase 5 archive clue filters,
 * and the Phase 6 search endpoint.
 */
class ClueSearchFlagsFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // Phase 3 — favorites & pins
    // ------------------------------------------------------------------

    @Test
    void favoriteToggleFeedsTheFavoritesOnlyFilter() throws Exception {
        String token = registerVerified("fave@example.com", "fave");

        String a = createMemory(token, "First", "2026-09-13", null).get("id").asText();
        String b = createMemory(token, "Second", "2026-09-14", null).get("id").asText();

        setFlags(token, a, true, false);

        JsonNode all = node(send(HttpMethod.GET, "/api/memories?favorite=false", null, token));
        assertThat(all.get("items").size()).isEqualTo(2);
        assertThat(all.get("total").asLong()).isEqualTo(2);

        JsonNode favs = node(send(HttpMethod.GET, "/api/memories?favorite=true", null, token));
        assertThat(favs.get("total").asLong()).isEqualTo(1);
        assertThat(favs.get("items").get(0).get("id").asText()).isEqualTo(a);
        assertThat(favs.get("items").get(0).get("favorite").asBoolean()).isTrue();
        assertThat(favs.get("items").get(0).get("pinned").asBoolean()).isFalse();

        // Unfavorite and the filter empties.
        setFlags(token, a, false, false);
        JsonNode empty = node(send(HttpMethod.GET, "/api/memories?favorite=true", null, token));
        assertThat(empty.get("total").asLong()).isZero();
    }

    @Test
    void pinLimitFiveIsEnforcedAndFreedByUnpinAndClearedByTrash() throws Exception {
        String token = registerVerified("pinner@example.com", "pinner");

        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            JsonNode created = createMemory(token, "Pinnable " + i, "2026-09-1" + i, null);
            ids.add(created.get("id").asText());
            setFlags(token, ids.get(i - 1), false, true);
        }

        JsonNode sixth = createMemory(token, "Sixth", "2026-09-16", null);
        MvcResult overLimit = send(HttpMethod.PATCH, "/api/memories/" + sixth.get("id").asText() + "/flags",
                Map.of("favorite", false, "pinned", true), token);
        assertThat(overLimit.getResponse().getStatus()).isEqualTo(400);

        // Freeing a slot by unpinning lets the 6th pin succeed.
        setFlags(token, ids.get(0), false, false);
        MvcResult nowOk = send(HttpMethod.PATCH, "/api/memories/" + sixth.get("id").asText() + "/flags",
                Map.of("favorite", false, "pinned", true), token);
        assertThat(nowOk.getResponse().getStatus()).isEqualTo(200);
        assertThat(node(nowOk).get("pinned").asBoolean()).isTrue();

        // Trash clears the pin so the memory cannot come back pinned.
        MvcResult trashed = send(HttpMethod.DELETE, "/api/memories/" + sixth.get("id").asText(), null, token);
        assertThat(node(trashed).get("pinned").asBoolean()).isFalse();
        assertThat(node(trashed).get("status").asText()).isEqualTo("TRASHED");
    }

    // ------------------------------------------------------------------
    // Phase 4 — clue model: mood, categories, people, places
    // ------------------------------------------------------------------

    @Test
    void cluesAreAttachedAndListedPerUser() throws Exception {
        String token = registerVerified("clued@example.com", "clued");

        JsonNode created = createMemory(token, "Trip notes", "2026-09-13", Map.of(
                "status", "COMPLETED",
                "mood", "EXCITED",
                "categories", List.of("Goa"),
                "people", List.of("Rahul"),
                "places", List.of("Café Blue")));

        assertThat(created.get("mood").asText()).isEqualTo("EXCITED");
        assertThat(created.get("categories").size()).isEqualTo(1);
        assertThat(created.get("categories").get(0).get("name").asText()).isEqualTo("Goa");
        assertThat(created.get("people").get(0).get("name").asText()).isEqualTo("Rahul");
        assertThat(created.get("places").get(0).get("name").asText()).isEqualTo("Café Blue");

        assertThat(node(send(HttpMethod.GET, "/api/categories", null, token)).size()).isEqualTo(1);
        assertThat(node(send(HttpMethod.GET, "/api/people", null, token)).size()).isEqualTo(1);
        assertThat(node(send(HttpMethod.GET, "/api/places", null, token)).size()).isEqualTo(1);
    }

    @Test
    void clueNamesAreCaseInsensitiveReused() throws Exception {
        String token = registerVerified("cluecs@example.com", "cluecs");

        createMemory(token, "First", "2026-09-13", Map.of("categories", List.of("Goa")));
        createMemory(token, "Second", "2026-09-14", Map.of("categories", List.of("goa")));
        createMemory(token, "Third", "2026-09-15", Map.of("people", List.of("RAHUL")));
        createMemory(token, "Fourth", "2026-09-16", Map.of("people", List.of("rahul")));

        assertThat(node(send(HttpMethod.GET, "/api/categories", null, token)).size()).isEqualTo(1);
        assertThat(node(send(HttpMethod.GET, "/api/categories", null, token)).get(0).get("name").asText())
                .isEqualTo("Goa");
        assertThat(node(send(HttpMethod.GET, "/api/people", null, token)).size()).isEqualTo(1);
    }

    @Test
    void emptyingCluesAndMoodClearsThemOnUpdate() throws Exception {
        String token = registerVerified("clearer@example.com", "clearer");

        JsonNode created = createMemory(token, "Was clued", "2026-09-13", Map.of(
                "status", "COMPLETED",
                "mood", "SAD",
                "tags", List.of("keepme"),
                "categories", List.of("Goa"),
                "people", List.of("Rahul"),
                "places", List.of("Café Blue")));
        String id = created.get("id").asText();

        // A LinkedHashMap so the payload really can carry "mood": null.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Was clued");
        payload.put("content", "Still clued text");
        payload.put("eventDate", "2026-09-13");
        payload.put("status", "COMPLETED");
        payload.put("mood", null);
        payload.put("tags", List.of("keepme"));
        payload.put("categories", List.of());
        payload.put("people", List.of());
        payload.put("places", List.of());

        MvcResult updated = send(HttpMethod.PUT, "/api/memories/" + id, payload, token);

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = node(updated);
        assertThat(body.hasNonNull("mood")).isTrue();
        assertThat(body.get("mood").isNull()).isTrue();
        assertThat(body.get("categories").size()).isZero();
        assertThat(body.get("people").size()).isZero();
        assertThat(body.get("places").size()).isZero();
        assertThat(body.get("tags").size()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Phase 5 — archive clue filters
    // ------------------------------------------------------------------

    @Test
    void clueFiltersAreAndAcrossTypesAndOrWithinType() throws Exception {
        String token = registerVerified("fisher@example.com", "fisher");

        JsonNode a = createMemory(token, "A", "2026-09-13",
                Map.of("status", "COMPLETED", "tags", List.of("T1"), "categories", List.of("C1"), "mood", "SAD"));
        JsonNode b = createMemory(token, "B", "2026-09-14",
                Map.of("status", "COMPLETED", "tags", List.of("T2"), "categories", List.of("C1")));
        JsonNode c = createMemory(token, "C", "2026-09-15",
                Map.of("status", "COMPLETED", "tags", List.of("T1"), "categories", List.of("C2")));

        // OR within a type: both T1 memories.
        JsonNode byTag = node(send(HttpMethod.GET, "/api/memories?tag=T1", null, token));
        assertThat(byTag.get("total").asLong()).isEqualTo(2);
        assertThat(idsOf(byTag)).containsExactlyInAnyOrder(a.get("id").asText(), c.get("id").asText());

        // AND across types: T1 AND C1 is only A.
        JsonNode byTagAndCat = node(send(HttpMethod.GET, "/api/memories?tag=T1&category=C1", null, token));
        assertThat(byTagAndCat.get("total").asLong()).isEqualTo(1);
        assertThat(byTagAndCat.get("items").get(0).get("id").asText()).isEqualTo(a.get("id").asText());

        // Mood alone.
        JsonNode byMood = node(send(HttpMethod.GET, "/api/memories?mood=SAD", null, token));
        assertThat(byMood.get("total").asLong()).isEqualTo(1);
        // Mood + category: A matches both.
        JsonNode moodAndCat = node(send(HttpMethod.GET, "/api/memories?mood=SAD&category=C1", null, token));
        assertThat(moodAndCat.get("total").asLong()).isEqualTo(1);

        // Empty intersection returns none, not an error.
        JsonNode none = node(send(HttpMethod.GET, "/api/memories?tag=T2&category=C2", null, token));
        assertThat(none.get("total").asLong()).isZero();

        // Favorites combine with clue filters.
        setFlags(token, b.get("id").asText(), true, false);
        JsonNode favT1 = node(send(HttpMethod.GET, "/api/memories?favorite=true&tag=T1", null, token));
        assertThat(favT1.get("total").asLong()).isZero();
        JsonNode favT2 = node(send(HttpMethod.GET, "/api/memories?favorite=true&tag=T2", null, token));
        assertThat(favT2.get("total").asLong()).isEqualTo(1);
        assertThat(favT2.get("items").get(0).get("id").asText()).isEqualTo(b.get("id").asText());
    }

    @Test
    void clueFiltersRespectTheDateRange() throws Exception {
        String token = registerVerified("dater@example.com", "dater");

        createMemory(token, "Old", "2026-09-10", Map.of("tags", List.of("T1")));
        createMemory(token, "New", "2026-09-20", Map.of("tags", List.of("T1")));

        JsonNode ranged = node(send(HttpMethod.GET, "/api/memories?tag=T1&from=2026-09-15", null, token));
        assertThat(ranged.get("total").asLong()).isEqualTo(1);
        assertThat(ranged.get("items").get(0).get("title").asText()).isEqualTo("New");

        MvcResult invalid = send(HttpMethod.GET, "/api/memories?tag=T1&from=2026-09-20&to=2026-09-01", null, token);
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // Phase 6 — search
    // ------------------------------------------------------------------

    @Test
    void searchMatchesTextAndEveryClueTypeIncludeDraftsExcludeTrash() throws Exception {
        String token = registerVerified("searcher@example.com", "searcher");

        JsonNode rainy = createMemory(token, "Rainy day in Mumbai", "2026-09-15", Map.of(
                "status", "COMPLETED", "content", "walking to the station in the rain",
                "tags", List.of("office"), "places", List.of("Station Rd")));
        JsonNode coffee = createMemory(token, "Seaside walk", "2026-09-14", Map.of(
                "status", "COMPLETED", "content", "coffee near the sea at dusk",
                "categories", List.of("weekend"), "places", List.of("Café Blue")));
        JsonNode draft = createMemory(token, "", "2026-09-16", Map.of(
                "content", "an unfinished idea about running a bookshop"));

        assertThat(rainy.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");

        // Title, content, and each clue type.
        assertThat(idsOf(node(send(HttpMethod.GET, "/api/memories/search?q=Mumbai", null, token))))
                .containsExactly(rainy.get("id").asText());
        assertThat(idsOf(node(send(HttpMethod.GET, "/api/memories/search?q=coffee", null, token))))
                .containsExactly(coffee.get("id").asText());
        assertThat(idsOf(node(send(HttpMethod.GET, "/api/memories/search?q=office", null, token))))
                .containsExactly(rainy.get("id").asText());
        assertThat(idsOf(node(send(HttpMethod.GET, "/api/memories/search?q=weekend", null, token))))
                .containsExactly(coffee.get("id").asText());
        assertThat(idsOf(node(send(HttpMethod.GET, "/api/memories/search?q=Blue", null, token))))
                .containsExactly(coffee.get("id").asText());

        // Drafts are searchable.
        JsonNode draftResults = node(send(HttpMethod.GET, "/api/memories/search?q=bookshop", null, token));
        assertThat(idsOf(draftResults)).containsExactly(draft.get("id").asText());

        // OR by default: both memories match either word.
        JsonNode orResults = node(send(HttpMethod.GET, "/api/memories/search?q=Mumbai%20coffee", null, token));
        assertThat(idsOf(orResults)).containsExactlyInAnyOrder(rainy.get("id").asText(), coffee.get("id").asText());

        // '+' requires the token: only the memory containing both.
        JsonNode andResults = node(send(HttpMethod.GET, "/api/memories/search?q=%2BMumbai%20station", null, token));
        assertThat(idsOf(andResults)).containsExactly(rainy.get("id").asText());
        JsonNode noAndResults = node(send(HttpMethod.GET, "/api/memories/search?q=%2BMumbai%20%2Bcoffee", null, token));
        assertThat(noAndResults.get("items").size()).isZero();

        // Trashed memories never match.
        send(HttpMethod.DELETE, "/api/memories/" + coffee.get("id").asText(), null, token);
        assertThat(idsOf(node(send(HttpMethod.GET, "/api/memories/search?q=coffee", null, token)))).isEmpty();

        // Blank query returns an empty page, not an error.
        JsonNode blank = node(send(HttpMethod.GET, "/api/memories/search?q=", null, token));
        assertThat(blank.get("total").asLong()).isZero();
        assertThat(blank.get("items").size()).isZero();
    }

    @Test
    void searchIsNewestEventDateFirstAndPaginated() throws Exception {
        String token = registerVerified("searcher2@example.com", "searcher2");

        JsonNode old = createMemory(token, "Rainy entry", "2026-09-10", null);
        JsonNode mid = createMemory(token, "Rainy entry", "2026-09-11", null);
        JsonNode new_ = createMemory(token, "Rainy entry", "2026-09-12", null);

        JsonNode firstPage = node(send(HttpMethod.GET, "/api/memories/search?q=Rainy&limit=2&offset=0", null, token));
        assertThat(firstPage.get("total").asLong()).isEqualTo(3);
        assertThat(firstPage.get("hasMore").asBoolean()).isTrue();
        assertThat(idsOf(firstPage)).containsExactly(new_.get("id").asText(), mid.get("id").asText());

        JsonNode lastPage = node(send(HttpMethod.GET, "/api/memories/search?q=Rainy&limit=2&offset=2", null, token));
        assertThat(lastPage.get("hasMore").asBoolean()).isFalse();
        assertThat(idsOf(lastPage)).containsExactly(old.get("id").asText());
    }

    @Test
    void searchEscapesWildcardsAndNeverLeaksOtherUsers() throws Exception {
        String tokenA = registerVerified("searcher3a@example.com", "searcher3a");
        String tokenB = registerVerified("searcher3b@example.com", "searcher3b");

        createMemory(tokenA, "Percentages", "2026-09-13",
                Map.of("content", "I gave it 100% effort today"));
        createMemory(tokenB, "His own", "2026-09-13", Map.of("content", "a private thought"));

        // '%' in the query must match a literal percent, not act as a wildcard.
        JsonNode literal = node(send(HttpMethod.GET, "/api/memories/search?q=100%25effort", null, tokenA));
        assertThat(literal.get("total").asLong()).isEqualTo(1);
        // A '%' alone (wildcard if unescaped) still matches nothing on its own.
        JsonNode wildcard = node(send(HttpMethod.GET, "/api/memories/search?q=%25%25", null, tokenA));
        assertThat(wildcard.get("total").asLong()).isZero();

        // User isolation: B cannot see A's memories.
        assertThat(idsOf(node(send(HttpMethod.GET, "/api/memories/search?q=effort", null, tokenB)))).isEmpty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private JsonNode createMemory(String token, String title, String eventDate, Map<String, Object> extra)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", "default content body");
        body.put("eventDate", eventDate);
        if (extra != null) {
            body.putAll(extra);
        }
        MvcResult result = send(HttpMethod.POST, "/api/memories", body, token);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return node(result);
    }

    private void setFlags(String token, String id, boolean favorite, boolean pinned) throws Exception {
        MvcResult result = send(HttpMethod.PATCH, "/api/memories/" + id + "/flags",
                Map.of("favorite", favorite, "pinned", pinned), token);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    private List<String> idsOf(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private String registerVerified(String email, String username) throws Exception {
        MvcResult result = send(HttpMethod.POST, "/api/auth/register",
                Map.of("email", email, "username", username, "password", "secret1234"), null);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setEmailVerified(true);
            userRepository.save(user);
        });
        return loginAccess(email, "secret1234");
    }

    private String loginAccess(String login, String password) throws Exception {
        MvcResult result = send(HttpMethod.POST, "/api/auth/login",
                Map.of("login", login, "password", password), null);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return node(result).get("accessToken").asText();
    }

    private MvcResult send(HttpMethod method, String url, Object body, String token) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, url);
        builder.contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsBytes(body));
        }
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode node(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}