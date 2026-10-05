package in.abdulmajid.lifeclues.memory.dto;

import jakarta.validation.constraints.NotNull;

public record MemoryFlagsRequest(
        @NotNull(message = "favorite is required")
        Boolean favorite,
        @NotNull(message = "pinned is required")
        Boolean pinned) {
}