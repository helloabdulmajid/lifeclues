package in.abdulmajid.lifeclues.feedback.entity;

import in.abdulmajid.lifeclues.account.entity.User;
import in.abdulmajid.lifeclues.feedback.dto.FeedbackCategory;
import in.abdulmajid.lifeclues.feedback.dto.FeedbackStatus;
import in.abdulmajid.lifeclues.feedback.dto.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's feedback/support report. Stored in the database first — the
 * notification email is a best-effort side effect and never a prerequisite.
 * The user link is nullable so account deletion can anonymize the row while
 * keeping non-identifying content for product statistics.
 */
@Entity
@Table(name = "feedback", indexes = {
        @Index(name = "idx_feedback_user", columnList = "user_id"),
        @Index(name = "idx_feedback_status", columnList = "status"),
        @Index(name = "idx_feedback_category", columnList = "category"),
        @Index(name = "idx_feedback_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Null after the submitting account has been deleted (anonymization). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackCategory category;

    @Column(nullable = false, length = 120)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "reproduction_steps", columnDefinition = "text")
    private String reproductionSteps;

    /** Only stored when the user chose to leave contact details. */
    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status = FeedbackStatus.NEW;

    /** PENDING until the post-save notification attempt records its real outcome. */
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_status", nullable = false, length = 20)
    private NotificationStatus notificationStatus = NotificationStatus.PENDING;

    @Column(name = "notification_error", columnDefinition = "text")
    private String notificationError;

    /** Screenshot attachment metadata — no file path until private storage exists. */
    @Column(name = "attachment_filename", length = 255)
    private String attachmentFilename;

    @Column(name = "attachment_mime_type", length = 100)
    private String attachmentMimeType;

    @Column(name = "attachment_size_bytes")
    private Long attachmentSizeBytes;

    @Column(name = "has_attachment", nullable = false)
    private boolean hasAttachment = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
