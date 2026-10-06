package in.abdulmajid.lifeclues.feedback.controller;

import in.abdulmajid.lifeclues.feedback.dto.FeedbackRequest;
import in.abdulmajid.lifeclues.feedback.dto.FeedbackResponse;
import in.abdulmajid.lifeclues.feedback.service.FeedbackService;
import in.abdulmajid.lifeclues.security.CurrentUser;
import in.abdulmajid.lifeclues.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Authenticated-only feedback submission — the user id comes from the trusted
 * server-side principal, never from the request body.
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse submit(@CurrentUser UserPrincipal currentUser,
                                   @ModelAttribute @Valid FeedbackRequest request,
                                   @RequestParam(value = "screenshot", required = false)
                                   MultipartFile screenshot) {
        return feedbackService.submit(currentUser.getId(), request, screenshot);
    }
}
