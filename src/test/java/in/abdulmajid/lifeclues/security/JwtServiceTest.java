package in.abdulmajid.lifeclues.security;

import in.abdulmajid.lifeclues.account.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService(byte[] keyBytes, long minutes) {
        return new JwtService(Base64.getEncoder().encodeToString(keyBytes), minutes);
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("alice@example.com");
        user.setUsername("alice");
        return user;
    }

    @Test
    void generatedTokenIsValidAndCarriesUserId() {
        JwtService service = jwtService(new byte[64], 15);
        User user = user();

        String token = service.generateAccessToken(user);

        assertThat(service.isValid(token)).isTrue();
        assertThat(service.extractUserId(token)).isEqualTo(user.getId());
    }

    @Test
    void blankOrNullTokenIsRejected() {
        JwtService service = jwtService(new byte[64], 15);
        assertThat(service.isValid(null)).isFalse();
        assertThat(service.isValid("   ")).isFalse();
        assertThat(service.isValid("")).isFalse();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = jwtService(new byte[64], 15);
        String token = service.generateAccessToken(user());

        String prefix = token.substring(0, token.length() - 2) + "xx";
        assertThat(service.isValid(prefix)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService signer = jwtService(new byte[64], 15);
        JwtService verifier = jwtService(new byte[64], 15);
        byte[] otherKey = new byte[64];
        otherKey[0] = 42;
        JwtService otherVerifier = jwtService(otherKey, 15);

        String token = signer.generateAccessToken(user());

        assertThat(verifier.isValid(token)).isTrue();
        assertThat(otherVerifier.isValid(token)).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        byte[] key = new byte[64];
        JwtService service = jwtService(key, 0);

        String token = service.generateAccessToken(user());

        assertThat(service.isValid(token)).isFalse();
    }

    @Test
    void accessExpiryIsExposedInSeconds() {
        JwtService service = jwtService(new byte[64], 15);
        assertThat(service.getAccessExpirySeconds()).isEqualTo(900);
    }
}