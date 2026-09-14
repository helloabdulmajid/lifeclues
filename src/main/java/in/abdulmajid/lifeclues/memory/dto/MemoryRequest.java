package in.abdulmajid.lifeclues.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record MemoryRequest(
        @Size(max = 120, message = "Title must be at most 120 characters")
        String title,

        @NotBlank(message = "Journal content is required")
        @Size(max = 10000, message = "Journal content must be at most 10000 characters")
        String content,

        @NotNull(message = "Event date is required")
        LocalDate eventDate,

        LocalTime eventTime,

        MemoryStatus status,

        @Size(max = 50, message = "Maximum 50 tags per memory")
        List<String> tags) {
}