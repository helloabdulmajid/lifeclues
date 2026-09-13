package in.abdulmajid.lifeclues.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @NotBlank(message = "Username is required")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]{3,30}$",
                message = "Username must be 3-30 characters using letters, numbers, dots, dashes or underscores")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @Size(max = 100, message = "Display name must be at most 100 characters")
        String displayName) {
}