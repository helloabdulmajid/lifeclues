package in.abdulmajid.lifeclues.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import in.abdulmajid.lifeclues.account.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessExpirySeconds;

    public JwtService(@Value("${lifeclues.jwt.secret}") String secret,
                      @Value("${lifeclues.jwt.access-expiry-minutes:15}") long accessExpiryMinutes) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "LC_JWT_SECRET is not set. Copy .env.example to .env and fill it in (run: openssl rand -base64 64).");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "LC_JWT_SECRET is not valid base64. Generate a fresh one with: openssl rand -base64 64", ex);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "LC_JWT_SECRET is too short: it must decode to at least 32 bytes. Generate one with: openssl rand -base64 64");
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpirySeconds = accessExpiryMinutes * 60;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("username", user.getUsername())
                .issuer("lifeclues")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessExpirySeconds)))
                .signWith(key)
                .compact();
    }

    public boolean isValid(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return UUID.fromString(claims.getSubject());
    }

    public long getAccessExpirySeconds() {
        return accessExpirySeconds;
    }
}