package in.abdulmajid.lifeclues.memory.dto;

import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(
        @NotNull(message = "Status is required")
        MemoryStatus status) {
}