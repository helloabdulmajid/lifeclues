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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

class MemoryFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // Creating
    // ------------------------------------------------------------------

    @Test
    void createDraftAndCompletedMemories() throws Exception {
        String token = registerVerified("nora@example.com", "nora");

        MvcResult draft = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "A quiet morning", "eventDate", "2026-09-12"), token);
        assertThat(draft.getResponse().getStatus()).isEqualTo(201);
        assertThat(node(draft).get("status").asText()).isEqualTo("DRAFT");

        MvcResult completed = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "A great evening", "eventDate", "2026-09-13",
                        "status", "COMPLETED"), token);
        assertThat(completed.getResponse().getStatus()).isEqualTo(201);
        assertThat(node(completed).get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void createWithTrashedStatusIsRejected() throws Exception {
        String token = registerVerified("olive@example.com", "olive");

        MvcResult result = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "Nope", "eventDate", "2026-09-13",
                        "status", "TRASHED"), token);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // Auto-title
    // ------------------------------------------------------------------

    @Test
    void blankTitleIsGeneratedFromContent() throws Exception {
        String token = registerVerified("pearl@example.com", "pearl");

        String shortContent = "Rain on the tin roof.";
        MvcResult shortMemory = send(HttpMethod.POST, "/api/memories",
                Map.of("content", shortContent, "eventDate", "2026-09-13"), token);
        assertThat(node(shortMemory).get("title").asText()).isEqualTo(shortContent);

        String longContent = "A very long first line that goes on and on about the small market "
                + "at the corner where the baker always remembers my name and the morning smells of yeast. "
                + "This sentence simply does not end, it just keeps unwinding like a spool of thread.";
        MvcResult longMemory = send(HttpMethod.POST, "/api/memories",
                Map.of("content", longContent, "eventDate", "2026-09-13"), token);
        JsonNode created = node(longMemory);
        assertThat(created.get("title").asText()).endsWith("…");
        assertThat(created.get("title").asText().length()).isLessThanOrEqualTo(61);

        MvcResult custom = send(HttpMethod.POST, "/api/memories",
                Map.of("title", "My own title", "content", shortContent, "eventDate", "2026-09-13"), token);
        assertThat(node(custom).get("title").asText()).isEqualTo("My own title");
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    void blankContentAndMissingEventDateAreRejected() throws Exception {
        String token = registerVerified("quin@example.com", "quin");

        MvcResult emptyContent = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "   ", "eventDate", "2026-09-13"), token);
        assertThat(emptyContent.getResponse().getStatus()).isEqualTo(400);
        assertThat(node(emptyContent).get("fieldErrors").get("content")).isNotNull();

        MvcResult missingDate = send(HttpMethod.POST, "/api/memories", Map.of("content", "hello"), token);
        assertThat(missingDate.getResponse().getStatus()).isEqualTo(400);
        assertThat(node(missingDate).get("fieldErrors").get("eventDate")).isNotNull();
    }

    // ------------------------------------------------------------------
    // Listing + ordering
    // ------------------------------------------------------------------

    @Test
    void memoriesAreListedNewestEventDateFirstAndPaged() throws Exception {
        String token = registerVerified("rose@example.com", "rose");

        for (int day = 1; day <= 5; day++) {
            send(HttpMethod.POST, "/api/memories",
                    Map.of("content", "Memory " + day, "eventDate", "2026-09-1" + day), token);
        }

        MvcResult firstPage = send(HttpMethod.GET, "/api/memories?limit=2&offset=0", null, token);
        JsonNode pageOne = node(firstPage);
        assertThat(pageOne.get("hasMore").asBoolean()).isTrue();
        assertThat(pageOne.get("items").size()).isEqualTo(2);
        assertThat(pageOne.get("items").get(0).get("eventDate").asText()).isEqualTo("2026-09-15");
        assertThat(pageOne.get("items").get(1).get("eventDate").asText()).isEqualTo("2026-09-14");

        MvcResult lastPage = send(HttpMethod.GET, "/api/memories?limit=2&offset=4", null, token);
        JsonNode pageLast = node(lastPage);
        assertThat(pageLast.get("items").size()).isEqualTo(1);
        assertThat(pageLast.get("items").get(0).get("eventDate").asText()).isEqualTo("2026-09-11");
        assertThat(pageLast.get("total").asLong()).isEqualTo(5);
    }

    // ------------------------------------------------------------------
    // Security / isolation
    // ------------------------------------------------------------------

    @Test
    void aUserCanNeverTouchAnotherUsersMemory() throws Exception {
        String tokenA = registerVerified("sam@example.com", "sam");
        String tokenB = registerVerified("talia@example.com", "talia");

        MvcResult createdA = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "Sam's secret", "eventDate", "2026-09-13"), tokenA);
        String memoryId = node(createdA).get("id").asText();

        MvcResult listForB = send(HttpMethod.GET, "/api/memories", null, tokenB);
        assertThat(node(listForB).get("items").size()).isEqualTo(0);
        assertThat(node(listForB).get("total").asLong()).isEqualTo(0);

        assertThat(send(HttpMethod.GET, "/api/memories/" + memoryId, null, tokenB).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(send(HttpMethod.PUT, "/api/memories/" + memoryId,
                Map.of("content", "hijack", "eventDate", "2026-09-13"), tokenB).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(send(HttpMethod.DELETE, "/api/memories/" + memoryId, null, tokenB).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(send(HttpMethod.POST, "/api/memories/" + memoryId + "/restore", null, tokenB)
                .getResponse().getStatus()).isEqualTo(404);

        MvcResult stillThere = send(HttpMethod.GET, "/api/memories/" + memoryId, null, tokenA);
        assertThat(stillThere.getResponse().getStatus()).isEqualTo(200);
        assertThat(node(stillThere).get("content").asText()).isEqualTo("Sam's secret");
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    @Test
    void updateChangesDateTimeTitleAndContentAnytime() throws Exception {
        String token = registerVerified("uma@example.com", "uma");

        MvcResult created = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "Original", "eventDate", "2026-09-13", "status", "COMPLETED"), token);
        String id = node(created).get("id").asText();

        MvcResult updated = send(HttpMethod.PUT, "/api/memories/" + id,
                Map.of("title", "Edited title", "content", "Rewritten body",
                        "eventDate", "2026-09-01", "eventTime", "09:30:00"), token);
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = node(updated);
        assertThat(body.get("title").asText()).isEqualTo("Edited title");
        assertThat(body.get("content").asText()).isEqualTo("Rewritten body");
        assertThat(body.get("eventDate").asText()).isEqualTo("2026-09-01");
        assertThat(body.get("eventTime").asText()).isEqualTo("09:30:00");
        assertThat(body.get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void trashedMemoryCannotBeEdited() throws Exception {
        String token = registerVerified("vera@example.com", "vera");

        MvcResult created = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "Doomed", "eventDate", "2026-09-13"), token);
        String id = node(created).get("id").asText();

        send(HttpMethod.DELETE, "/api/memories/" + id, null, token);

        MvcResult edit = send(HttpMethod.PUT, "/api/memories/" + id,
                Map.of("content", "edited", "eventDate", "2026-09-13"), token);
        assertThat(edit.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void statusCanBeToggledViaPatchButNotToTrash() throws Exception {
        String token = registerVerified("wren@example.com", "wren");

        MvcResult created = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "Toggle me", "eventDate", "2026-09-13"), token);
        String id = node(created).get("id").asText();

        MvcResult complete = send(HttpMethod.PATCH, "/api/memories/" + id + "/status",
                Map.of("status", "COMPLETED"), token);
        assertThat(node(complete).get("status").asText()).isEqualTo("COMPLETED");

        MvcResult toTrash = send(HttpMethod.PATCH, "/api/memories/" + id + "/status",
                Map.of("status", "TRASHED"), token);
        assertThat(toTrash.getResponse().getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // Trash lifecycle
    // ------------------------------------------------------------------

    @Test
    void trashHidesMemoryAndRestoreReturnsToPreviousStatus() throws Exception {
        String token = registerVerified("xena@example.com", "xena");

        MvcResult completed = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "Complete memory", "eventDate", "2026-09-13",
                        "status", "COMPLETED"), token);
        String completedId = node(completed).get("id").asText();
        MvcResult draft = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "Draft memory", "eventDate", "2026-09-12"), token);
        String draftId = node(draft).get("id").asText();

        MvcResult trashed = send(HttpMethod.DELETE, "/api/memories/" + completedId, null, token);
        JsonNode trashedBody = node(trashed);
        assertThat(trashedBody.get("status").asText()).isEqualTo("TRASHED");
        assertThat(trashedBody.hasNonNull("deletedAt")).isTrue();

        JsonNode list = node(send(HttpMethod.GET, "/api/memories", null, token));
        assertThat(list.get("items").size()).isEqualTo(1);
        assertThat(list.get("items").get(0).get("id").asText()).isEqualTo(draftId);

        JsonNode trashPage = node(send(HttpMethod.GET, "/api/memories/trash", null, token));
        assertThat(trashPage.get("items").size()).isEqualTo(1);
        assertThat(trashPage.get("items").get(0).get("id").asText()).isEqualTo(completedId);

        MvcResult restore = send(HttpMethod.POST, "/api/memories/" + completedId + "/restore", null, token);
        JsonNode restored = node(restore);
        assertThat(restored.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(restored.has("deletedAt")).isTrue();

        send(HttpMethod.DELETE, "/api/memories/" + draftId, null, token);
        JsonNode restoredDraft = node(
                send(HttpMethod.POST, "/api/memories/" + draftId + "/restore", null, token));
        assertThat(restoredDraft.get("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void restoringANonTrashedMemoryIsRejected() throws Exception {
        String token = registerVerified("yasmin@example.com", "yasmin");

        MvcResult created = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "Active", "eventDate", "2026-09-13"), token);
        String id = node(created).get("id").asText();

        MvcResult restore = send(HttpMethod.POST, "/api/memories/" + id + "/restore", null, token);
        assertThat(restore.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void permanentDeleteAndEmptyTrashRemoveMemories() throws Exception {
        String token = registerVerified("zara@example.com", "zara");

        MvcResult stay = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "Permanent", "eventDate", "2026-09-13"), token);
        MvcResult gone = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "Removed", "eventDate", "2026-09-12"), token);
        MvcResult keep = send(HttpMethod.POST, "/api/memories",
                Map.of("content", "Kept", "eventDate", "2026-09-11"), token);

        String stayId = node(stay).get("id").asText();
        String goneId = node(gone).get("id").asText();
        String keepId = node(keep).get("id").asText();

        // Permanent-delete the first one while it is still active.
        assertThat(send(HttpMethod.DELETE, "/api/memories/" + stayId + "/permanent", null, token)
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(send(HttpMethod.GET, "/api/memories/" + stayId, null, token).getResponse().getStatus())
                .isEqualTo(404);

        // Trash the other two, then empty the trash.
        send(HttpMethod.DELETE, "/api/memories/" + goneId, null, token);
        send(HttpMethod.DELETE, "/api/memories/" + keepId, null, token);

        MvcResult emptied = send(HttpMethod.DELETE, "/api/memories/trash", null, token);
        assertThat(emptied.getResponse().getStatus()).isEqualTo(200);
        assertThat(node(emptied).get("message").asText()).isEqualTo("Trash emptied");

        assertThat(node(send(HttpMethod.GET, "/api/memories/trash", null, token)).get("items").size())
                .isEqualTo(0);
        assertThat(send(HttpMethod.GET, "/api/memories/" + goneId, null, token).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/api/memories/" + keepId, null, token).getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/api/memories"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
        mockMvc.perform(request(HttpMethod.POST, "/api/memories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String registerVerified(String email, String username) throws Exception {
        MvcResult result = send(HttpMethod.POST, "/api/auth/register",
                Map.of("email", email, "username", username, "password", "secret1234"), null);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        verifyInDb(email);
        return loginAccess(email, "secret1234");
    }

    private void verifyInDb(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setEmailVerified(true);
            userRepository.save(user);
        });
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