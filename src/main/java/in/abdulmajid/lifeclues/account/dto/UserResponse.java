package in.abdulmajid.lifeclues.account.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        String bio,
        Instant createdAt,
        Instant updatedAt,
        LocalDate dateOfBirth,
        String phone,
        String gender,
        String city,
        String country,
        String profession,
        String relationshipStatus,
        String languages,
        boolean emailVerified) {
}