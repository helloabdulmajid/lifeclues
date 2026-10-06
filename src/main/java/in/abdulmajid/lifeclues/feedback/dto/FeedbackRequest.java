package in.abdulmajid.lifeclues.feedback.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The feedback form as it arrives via multipart/form-data.
 * The optional screenshot is bound separately as a MultipartFile param.
 */
public record FeedbackRequest(
        @NotNull(message = "Category is required")
        FeedbackCategory category,

        @NotBlank(message = "Subject is required")
        @Size(min = 5, max = 120, message = "Subject must be between 5 and 120 characters")
        String subject,

        @NotBlank(message = "Description is required")
        @Size(min = 20, max = 5000, message = "Description must be between 20 and 5000 characters")
        String description,

        @Size(max = 5000, message = "Reproduction steps must be at most 5000 characters")
        String reproductionSteps,

        @Size(max = 254, message = "Contact email must be at most 254 characters")
        @Email(message = "Contact email must be a valid email address")
        String contactEmail) {
}
