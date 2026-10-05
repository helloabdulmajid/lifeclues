package in.abdulmajid.lifeclues.memory.dto;

import java.util.UUID;

public record PersonResponse(
        UUID id,
        String name) {
}