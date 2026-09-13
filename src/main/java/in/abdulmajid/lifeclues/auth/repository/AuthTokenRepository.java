package in.abdulmajid.lifeclues.auth.repository;

import in.abdulmajid.lifeclues.auth.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHashAndType(String tokenHash, String type);

    List<AuthToken> findByUser_IdAndTypeAndUsedAtIsNull(UUID userId, String type);

    long deleteByUsedAtIsNotNull();

    long deleteByExpiresAtBefore(Instant now);
}