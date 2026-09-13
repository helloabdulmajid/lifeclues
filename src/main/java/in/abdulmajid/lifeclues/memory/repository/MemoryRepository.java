package in.abdulmajid.lifeclues.memory.repository;

import in.abdulmajid.lifeclues.memory.dto.MemoryStatus;
import in.abdulmajid.lifeclues.memory.entity.Memory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    /** Ownership-scoped lookup — never trust a client-supplied userId. */
    Optional<Memory> findByUserIdAndId(UUID userId, UUID id);

    Page<Memory> findByUserIdAndStatusNot(UUID userId, MemoryStatus status, Pageable pageable);

    Page<Memory> findByUserIdAndStatus(UUID userId, MemoryStatus status, Pageable pageable);

    long deleteByUserIdAndStatus(UUID userId, MemoryStatus status);

    /** Hard-deletes memories whose 30-day trash retention has lapsed. */
    long deleteByDeletedAtBefore(Instant cutoff);
}