package in.abdulmajid.lifeclues.account.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @Size(max = 100, message = "Display name must be at most 100 characters")
        String displayName,

        @Size(max = 2000, message = "Bio must be at most 2000 characters")
        String bio,

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @Size(max = 30, message = "Phone number must be at most 30 characters")
        String phone,

        @Size(max = 20, message = "Gender must be at most 20 characters")
        String gender,

        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @Size(max = 100, message = "Country must be at most 100 characters")
        String country,

        @Size(max = 100, message = "Profession must be at most 100 characters")
        String profession,

        @Size(max = 20, message = "Relationship status must be at most 20 characters")
        String relationshipStatus,

        @Size(max = 200, message = "Languages must be at most 200 characters")
        String languages) {
}