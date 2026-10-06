package in.abdulmajid.lifeclues.feedback.repository;

import in.abdulmajid.lifeclues.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    /** Used by the per-user submission rate limit (count within the last 24h). */
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    /** Step 1 of account-deletion anonymization: drop the matching contact email. */
    @Modifying
    @Query("update Feedback f set f.contactEmail = null "
            + "where f.user.id = :userId and lower(f.contactEmail) = :email")
    int clearMatchingContactEmail(@Param("userId") UUID userId, @Param("email") String emailLower);

    /** Step 2: unlink the rows from the deleted account (order matters — run after the email clear). */
    @Modifying
    @Query("update Feedback f set f.user = null where f.user.id = :userId")
    int detachUser(@Param("userId") UUID userId);
}
