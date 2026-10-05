package in.abdulmajid.lifeclues.memory.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record MemoryResponse(
        UUID id,
        String title,
        String content,
        LocalDate eventDate,
        LocalTime eventTime,
        MemoryStatus status,
        Mood mood,
        List<TagResponse> tags,
        List<CategoryResponse> categories,
        List<PersonResponse> people,
        List<PlaceResponse> places,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        boolean favorite,
        boolean pinned) {
}