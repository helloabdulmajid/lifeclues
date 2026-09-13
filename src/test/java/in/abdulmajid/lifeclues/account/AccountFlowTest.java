package in.abdulmajid.lifeclues.account;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import in.abdulmajid.lifeclues.account.entity.User;
import in.abdulmajid.lifeclues.testing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class AccountFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void updateOwnProfile() throws Exception {
        String email = "profile@example.com";
        String accessToken = registerAndLogin(email, "profilefan", "secret1234");

        MvcResult update = mockMvc.perform(put("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "displayName", "Grace Hopper",
                                "bio", "I build compilers."
                        ))))
                .andReturn();

        assertThat(update.getResponse().getStatus()).isEqualTo(200);
        JsonNode updated = node(update);
        assertThat(updated.get("displayName").asText()).isEqualTo("Grace Hopper");
        assertThat(updated.get("bio").asText()).isEqualTo("I build compilers.");

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(result -> {
                    JsonNode me = node(result);
                    assertThat(me.get("displayName").asText()).isEqualTo("Grace Hopper");
                    assertThat(me.get("bio").asText()).isEqualTo("I build compilers.");
                });
    }

    @Test
    void changePasswordInvalidatesOldPassword() throws Exception {
        String email = "pwd@example.com";
        registerAndLogin(email, "pwdfan", "secret1234");

        MvcResult change = mockMvc.perform(post("/api/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginAccess(email, "secret1234")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "currentPassword", "secret1234",
                                "newPassword", "newsecret5678"
                        ))))
                .andReturn();
        assertThat(change.getResponse().getStatus()).isEqualTo(200);

        assertThat(loginAccessOk(email, "newsecret5678")).isTrue();
        assertThat(loginOk(email, "secret1234")).isFalse();
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() throws Exception {
        String email = "wrongpwd@example.com";
        registerAndLogin(email, "wrongpwdfan", "secret1234");

        MvcResult change = mockMvc.perform(post("/api/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginAccess(email, "secret1234")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "currentPassword", "not-the-password",
                                "newPassword", "newsecret5678"
                        ))))
                .andReturn();
        assertThat(change.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void passwordIsStoredHashed() throws Exception {
        String email = "hashcheck@example.com";
        postJson("/api/auth/register",
                Map.of("email", email, "username", "hashcheck", "password", "secret1234"));

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("secret1234");
        assertThat(stored.getPasswordHash()).startsWith("$2");
    }

    private String registerAndLogin(String email, String username, String password) throws Exception {
        postJson("/api/auth/register", Map.of("email", email, "username", username, "password", password));
        return loginAccess(email, password);
    }

    private String loginAccess(String login, String password) throws Exception {
        MvcResult result = postJson("/api/auth/login", Map.of("login", login, "password", password));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return node(result).get("accessToken").asText();
    }

    private boolean loginAccessOk(String login, String password) throws Exception {
        MvcResult result = postJson("/api/auth/login", Map.of("login", login, "password", password));
        return result.getResponse().getStatus() == 200;
    }

    private boolean loginOk(String login, String password) throws Exception {
        return loginAccessOk(login, password);
    }

    private MvcResult postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();
    }

    private JsonNode node(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}