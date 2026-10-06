package in.abdulmajid.lifeclues.feedback.mapper;

import in.abdulmajid.lifeclues.feedback.dto.FeedbackResponse;
import in.abdulmajid.lifeclues.feedback.entity.Feedback;
import org.springframework.stereotype.Component;

@Component
public class FeedbackMapper {

    public FeedbackResponse toResponse(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getId(),
                reference(feedback),
                feedback.getCategory(),
                feedback.getStatus(),
                feedback.getNotificationStatus());
    }

    /** Short human-friendly handle for the confirmation screen: LC-XXXXXXXX. */
    private String reference(Feedback feedback) {
        return "LC-" + feedback.getId().toString().substring(0, 8).toUpperCase();
    }
}
