package in.abdulmajid.lifeclues.feedback.dto;

import java.util.UUID;

public record FeedbackResponse(
        UUID id,
        String reference,
        FeedbackCategory category,
        FeedbackStatus status,
        NotificationStatus notificationStatus) {
}
