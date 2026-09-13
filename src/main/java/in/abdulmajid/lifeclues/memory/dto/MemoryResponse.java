package in.abdulmajid.lifeclues.memory.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record MemoryResponse(
        UUID id,
        String title,
        String content,
        LocalDate eventDate,
        LocalTime eventTime,
        MemoryStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}