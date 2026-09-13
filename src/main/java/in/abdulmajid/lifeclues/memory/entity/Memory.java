package in.abdulmajid.lifeclues.memory.entity;

import in.abdulmajid.lifeclues.account.entity.User;
import in.abdulmajid.lifeclues.memory.dto.MemoryStatus;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** A single memory in a user's memory book. */
@Entity
@Table(name = "memories", indexes = {
        @Index(name = "idx_memories_user_event", columnList = "user_id, event_date DESC, created_at DESC"),
        @Index(name = "idx_memories_user_status", columnList = "user_id, status"),
        @Index(name = "idx_memories_sweep", columnList = "deleted_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Memory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_time")
    private LocalTime eventTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemoryStatus status = MemoryStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "trashed_from", length = 20)
    private MemoryStatus trashedFrom;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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