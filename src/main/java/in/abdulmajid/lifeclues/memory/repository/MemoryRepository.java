package in.abdulmajid.lifeclues.memory.repository;

import in.abdulmajid.lifeclues.memory.dto.MemoryStatus;
import in.abdulmajid.lifeclues.memory.entity.Memory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MemoryRepository extends JpaRepository<Memory, UUID>, JpaSpecificationExecutor<Memory> {

    /** Ownership-scoped lookup — never trust a client-supplied userId. */
    Optional<Memory> findByUserIdAndId(UUID userId, UUID id);

    /** Composes dedicated queries for favorite + event-date combinations; Phase 5 clue filters go through {@link #findAll(Specification, Pageable)}. */
    Page<Memory> findByUserIdAndStatusNot(UUID userId, MemoryStatus status, Pageable pageable);

    Page<Memory> findByUserIdAndStatusNotAndEventDateGreaterThanEqual(
            UUID userId, MemoryStatus status, LocalDate from, Pageable pageable);

    Page<Memory> findByUserIdAndStatusNotAndEventDateLessThanEqual(
            UUID userId, MemoryStatus status, LocalDate to, Pageable pageable);

    Page<Memory> findByUserIdAndStatusNotAndEventDateBetween(
            UUID userId, MemoryStatus status, LocalDate from, LocalDate to, Pageable pageable);

    Page<Memory> findByUserIdAndStatusNotAndFavoriteTrue(
            UUID userId, MemoryStatus status, Pageable pageable);

    Page<Memory> findByUserIdAndStatusNotAndFavoriteTrueAndEventDateGreaterThanEqual(
            UUID userId, MemoryStatus status, LocalDate from, Pageable pageable);

    Page<Memory> findByUserIdAndStatusNotAndFavoriteTrueAndEventDateLessThanEqual(
            UUID userId, MemoryStatus status, LocalDate to, Pageable pageable);

    Page<Memory> findByUserIdAndStatusNotAndFavoriteTrueAndEventDateBetween(
            UUID userId, MemoryStatus status, LocalDate from, LocalDate to, Pageable pageable);

    Page<Memory> findByUserIdAndStatus(UUID userId, MemoryStatus status, Pageable pageable);

    long deleteByUserIdAndStatus(UUID userId, MemoryStatus status);

    long countByUserIdAndPinnedTrue(UUID userId);

    /** Hard-deletes memories whose 30-day trash retention has lapsed. */
    long deleteByDeletedAtBefore(Instant cutoff);
}