package in.abdulmajid.lifeclues.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import in.abdulmajid.lifeclues.auth.entity.AuthToken;
import in.abdulmajid.lifeclues.auth.entity.RefreshToken;
import in.abdulmajid.lifeclues.auth.repository.AuthTokenRepository;
import in.abdulmajid.lifeclues.testing.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Test
    void registerToLoginToMeByEmail() throws Exception {
        String email = "alice@example.com";
        String username = "alice";

        MvcResult registerResult = postJson("/api/auth/register",
                Map.of("email", email, "username", username, "password", "secret1234"));
        assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);

        JsonNode created = node(registerResult);
        assertThat(created.get("email").asText()).isEqualTo(email);
        assertThat(created.get("username").asText()).isEqualTo(username);
        assertThat(created.get("displayName").asText()).isEqualTo(username);
        assertThat(created.get("bio").asText()).isEmpty();
        assertThat(created.has("passwordHash")).isFalse();
        assertThat(created.hasNonNull("id")).isTrue();
        assertThat(created.hasNonNull("createdAt")).isTrue();

        String accessToken = loginAccess(email, "secret1234");

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(result -> {
                    JsonNode me = node(result);
                    assertThat(me.get("email").asText()).isEqualTo(email);
                    assertThat(me.get("username").asText()).isEqualTo(username);
                });
    }

    @Test
    void loginByUsernameAlsoWorks() throws Exception {
        String email = "bob@example.com";
        String username = "bob";

        postJson("/api/auth/register",
                Map.of("email", email, "username", username, "password", "secret1234"));

        MvcResult loginResult = postJson("/api/auth/login",
                Map.of("login", username, "password", "secret1234"));
        assertThat(loginResult.getResponse().getStatus()).isEqualTo(200);

        JsonNode auth = node(loginResult);
        assertThat(auth.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(auth.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(auth.get("user").get("username").asText()).isEqualTo(username);
    }

    @Test
    void emailIsNormalizedToLowerCase() throws Exception {
        MvcResult result = postJson("/api/auth/register",
                Map.of("email", " MIXEDCase@Example.COM ", "username", "casebook", "password", "secret1234"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(node(result).get("email").asText()).isEqualTo("mixedcase@example.com");
    }

    @Test
    void duplicateEmailAndUsernameAreRejectedWith409() throws Exception {
        postJson("/api/auth/register",
                Map.of("email", "dup@example.com", "username", "dupuser", "password", "secret1234"));

        MvcResult duplicateEmail = postJson("/api/auth/register",
                Map.of("email", "DUP@example.com", "username", "other", "password", "secret1234"));
        assertThat(duplicateEmail.getResponse().getStatus()).isEqualTo(409);

        MvcResult duplicateUsername = postJson("/api/auth/register",
                Map.of("email", "other@example.com", "username", "dupuser", "password", "secret1234"));
        assertThat(duplicateUsername.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void invalidRegisterPayloadReturns400() throws Exception {
        MvcResult badEmail = postJson("/api/auth/register",
                Map.of("email", "not-an-email", "username", "validname", "password", "secret1234"));
        assertThat(badEmail.getResponse().getStatus()).isEqualTo(400);
        assertThat(node(badEmail).get("fieldErrors").get("email")).isNotNull();

        MvcResult shortPassword = postJson("/api/auth/register",
                Map.of("email", "ok@example.com", "username", "validname", "password", "short"));
        assertThat(shortPassword.getResponse().getStatus()).isEqualTo(400);

        MvcResult badUsername = postJson("/api/auth/register",
                Map.of("email", "ok@example.com", "username", "@bad!", "password", "secret1234"));
        assertThat(badUsername.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void wrongPasswordAndUnknownUserReturn401() throws Exception {
        postJson("/api/auth/register",
                Map.of("email", "ellie@example.com", "username", "ellie", "password", "secret1234"));

        MvcResult wrongPassword = postJson("/api/auth/login",
                Map.of("login", "ellie@example.com", "password", "wrongpass1"));
        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);

        MvcResult unknownUser = postJson("/api/auth/login",
                Map.of("login", "ghost@example.com", "password", "secret1234"));
        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));

        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer("not.a.real.token")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
    }

    @Test
    void refreshRotatesTokensAndOldRefreshIsRejected() throws Exception {
        postJson("/api/auth/register",
                Map.of("email", "dave@example.com", "username", "dave", "password", "secret1234"));
        JsonNode auth = loginNode("dave@example.com", "secret1234");

        String oldRefresh = auth.get("refreshToken").asText();
        MvcResult refreshResult = postJson("/api/auth/refresh", Map.of("refreshToken", oldRefresh));
        assertThat(refreshResult.getResponse().getStatus()).isEqualTo(200);

        JsonNode refreshed = node(refreshResult);
        assertThat(refreshed.get("accessToken").asText()).isNotEqualTo(auth.get("accessToken").asText());
        assertThat(refreshed.get("refreshToken").asText()).isNotEqualTo(oldRefresh);

        MvcResult reuse = postJson("/api/auth/refresh", Map.of("refreshToken", oldRefresh));
        assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        postJson("/api/auth/register",
                Map.of("email", "erin@example.com", "username", "erin", "password", "secret1234"));
        JsonNode auth = loginNode("erin@example.com", "secret1234");
        String refresh = auth.get("refreshToken").asText();

        MvcResult logout = postJson("/api/auth/logout", Map.of("refreshToken", refresh));
        assertThat(logout.getResponse().getStatus()).isEqualTo(200);

        MvcResult afterLogout = postJson("/api/auth/refresh", Map.of("refreshToken", refresh));
        assertThat(afterLogout.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refreshTokensAreStoredHashedNotPlaintext() throws Exception {
        postJson("/api/auth/register",
                Map.of("email", "frank@example.com", "username", "frank", "password", "secret1234"));
        JsonNode auth = loginNode("frank@example.com", "secret1234");
        String raw = auth.get("refreshToken").asText();

        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getTokenHash)
                .noneMatch(raw::equals);
    }

    @Test
    void verifyEmailMarksAccountVerifiedAndTokenIsSingleUse() throws Exception {
        postJson("/api/auth/register",
                Map.of("email", "gina@example.com", "username", "gina", "password", "secret1234"));

        UUID userId = userRepository.findByEmail("gina@example.com").orElseThrow().getId();
        String raw = persistToken(userId, "VERIFY");

        MvcResult verify = postJson("/api/auth/verify-email", Map.of("token", raw));
        assertThat(verify.getResponse().getStatus()).isEqualTo(200);

        String accessToken = loginAccess("gina@example.com", "secret1234");
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(result -> assertThat(node(result).get("emailVerified").asBoolean()).isTrue());

        MvcResult reuse = postJson("/api/auth/verify-email", Map.of("token", raw));
        assertThat(reuse.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void invalidOrUnknownVerificationTokenReturns400() throws Exception {
        MvcResult garbage = postJson("/api/auth/verify-email", Map.of("token", "not-a-real-token"));
        assertThat(garbage.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void resetPasswordSwitchesPasswordAndRevokesRefreshTokens() throws Exception {
        postJson("/api/auth/register",
                Map.of("email", "henry@example.com", "username", "henry", "password", "secret1234"));

        UUID userId = userRepository.findByEmail("henry@example.com").orElseThrow().getId();
        String raw = persistToken(userId, "RESET");

        MvcResult reset = postJson("/api/auth/reset-password",
                Map.of("token", raw, "newPassword", "newsecret99"));
        assertThat(reset.getResponse().getStatus()).isEqualTo(200);

        MvcResult oldPassword = postJson("/api/auth/login",
                Map.of("login", "henry@example.com", "password", "secret1234"));
        assertThat(oldPassword.getResponse().getStatus()).isEqualTo(401);

        MvcResult newPassword = postJson("/api/auth/login",
                Map.of("login", "henry@example.com", "password", "newsecret99"));
        assertThat(newPassword.getResponse().getStatus()).isEqualTo(200);

        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void resetPasswordTokenIsSingleUse() throws Exception {
        postJson("/api/auth/register",
                Map.of("email", "ida@example.com", "username", "ida", "password", "secret1234"));

        UUID userId = userRepository.findByEmail("ida@example.com").orElseThrow().getId();
        String raw = persistToken(userId, "RESET");

        postJson("/api/auth/reset-password", Map.of("token", raw, "newPassword", "newsecret99"));

        MvcResult reuse = postJson("/api/auth/reset-password",
                Map.of("token", raw, "newPassword", "anotherpass1"));
        assertThat(reuse.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void forgotPasswordAndResendVerificationDoNotLeakAccountExistence() throws Exception {
        MvcResult known = postJson("/api/auth/forgot-password", Map.of("email", "ghost@example.com"));
        assertThat(known.getResponse().getStatus()).isEqualTo(200);

        MvcResult unknown = postJson("/api/auth/forgot-password", Map.of("email", "nobody@example.com"));
        assertThat(unknown.getResponse().getStatus()).isEqualTo(200);
        assertThat(node(unknown).get("message").asText()).contains("reset link");

        MvcResult resend = postJson("/api/auth/resend-verification", Map.of("email", "nobody@example.com"));
        assertThat(resend.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void registerResponseExposesEmailVerifiedField() throws Exception {
        MvcResult result = postJson("/api/auth/register",
                Map.of("email", "jill@example.com", "username", "jill", "password", "secret1234"));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(node(result).get("emailVerified").asBoolean()).isFalse();
    }

    private String persistToken(UUID userId, String type) throws NoSuchAlgorithmException {
        String raw = UUID.randomUUID().toString() + UUID.randomUUID();
        AuthToken token = new AuthToken();
        token.setUser(userRepository.getReferenceById(userId));
        token.setType(type);
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(Instant.now().plusSeconds(1800));
        token.setCreatedAt(Instant.now());
        authTokenRepository.save(token);
        return raw;
    }

    private String sha256(String value) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String loginAccess(String login, String password) throws Exception {
        return loginNode(login, password).get("accessToken").asText();
    }

    private JsonNode loginNode(String login, String password) throws Exception {
        MvcResult result = postJson("/api/auth/login", Map.of("login", login, "password", password));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return node(result);
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