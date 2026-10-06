package in.abdulmajid.lifeclues.feedback.service;

import in.abdulmajid.lifeclues.account.entity.User;
import in.abdulmajid.lifeclues.account.repository.UserRepository;
import in.abdulmajid.lifeclues.auth.mail.MailService;
import in.abdulmajid.lifeclues.common.exception.BadRequestException;
import in.abdulmajid.lifeclues.common.exception.ResourceNotFoundException;
import in.abdulmajid.lifeclues.common.exception.TooManyRequestsException;
import in.abdulmajid.lifeclues.feedback.dto.FeedbackCategory;
import in.abdulmajid.lifeclues.feedback.dto.FeedbackRequest;
import in.abdulmajid.lifeclues.feedback.dto.FeedbackResponse;
import in.abdulmajid.lifeclues.feedback.dto.FeedbackStatus;
import in.abdulmajid.lifeclues.feedback.dto.NotificationStatus;
import in.abdulmajid.lifeclues.feedback.entity.Feedback;
import in.abdulmajid.lifeclues.feedback.mapper.FeedbackMapper;
import in.abdulmajid.lifeclues.feedback.repository.FeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Saves feedback first (the database is the source of truth), then makes a
 * best-effort notification attempt and records its honest outcome on the row.
 * Email failure never removes or rolls back a saved report.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    static final int RATE_LIMIT_PER_DAY = 10;
    private static final long MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp");

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final FeedbackMapper mapper;
    private final String recipientConfig;
    private final String mailFrom;
    private final String smtpUsername;

    public FeedbackService(FeedbackRepository feedbackRepository,
                           UserRepository userRepository,
                           MailService mailService,
                           FeedbackMapper mapper,
                           @Value("${lifeclues.feedback.recipient:}") String recipientConfig,
                           @Value("${lifeclues.mail.from:}") String mailFrom,
                           @Value("${spring.mail.username:}") String smtpUsername) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.mapper = mapper;
        this.recipientConfig = recipientConfig;
        this.mailFrom = mailFrom;
        this.smtpUsername = smtpUsername;
    }

    @Transactional
    public FeedbackResponse submit(UUID userId, FeedbackRequest request, MultipartFile screenshot) {
        enforceRateLimit(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Feedback feedback = new Feedback();
        feedback.setUser(user);
        feedback.setCategory(request.category());
        feedback.setSubject(request.subject().trim());
        feedback.setDescription(request.description());
        feedback.setReproductionSteps(blankToNull(request.reproductionSteps()));
        feedback.setContactEmail(normalizeContactEmail(request.contactEmail()));
        feedback.setStatus(FeedbackStatus.NEW);
        feedback.setNotificationStatus(NotificationStatus.PENDING);

        byte[] attachmentBytes = readAndValidateAttachment(screenshot, feedback);

        // 1) Persist first — the report exists even if everything after fails.
        feedback = feedbackRepository.saveAndFlush(feedback);

        // 2) Best-effort notification, then 3) record the real outcome.
        String notificationError = null;
        NotificationStatus outcome = notifyTeam(feedback, user, attachmentBytes,
                feedback.getAttachmentMimeType(), feedback.getAttachmentFilename());
        if (outcome == NotificationStatus.FAILED) {
            notificationError = "SMTP send failed (see server log)";
        }
        feedback.setNotificationStatus(outcome);
        feedback.setNotificationError(notificationError);
        feedback = feedbackRepository.save(feedback);

        return mapper.toResponse(feedback);
    }

    /**
     * Account-deletion anonymization: unlink rows and drop a contact email
     * that directly identifies the deleted account; keep the (now anonymous)
     * content, status and timestamps for product statistics.
     */
    @Transactional
    public void anonymizeForUser(UUID userId, String accountEmail) {
        if (accountEmail != null && !accountEmail.isBlank()) {
            feedbackRepository.clearMatchingContactEmail(
                    userId, accountEmail.trim().toLowerCase(Locale.ROOT));
        }
        feedbackRepository.detachUser(userId);
    }

    private void enforceRateLimit(UUID userId) {
        Instant since = Instant.now().minus(Duration.ofHours(24));
        long recent = feedbackRepository.countByUserIdAndCreatedAtAfter(userId, since);
        if (recent >= RATE_LIMIT_PER_DAY) {
            throw new TooManyRequestsException(
                    "You've sent " + RATE_LIMIT_PER_DAY
                            + " feedback reports in the last 24 hours. Please try again later.");
        }
    }

    /**
     * Validates the optional screenshot (type + size + magic bytes) and writes
     * its metadata onto the row. Returns the in-memory bytes for the email —
     * the file itself is never stored anywhere until private storage exists.
     */
    private byte[] readAndValidateAttachment(MultipartFile screenshot, Feedback feedback) {
        if (screenshot == null || screenshot.isEmpty()) {
            return null;
        }
        String contentType = screenshot.getContentType() == null
                ? "" : screenshot.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BadRequestException("Screenshot must be a PNG, JPEG, or WebP image.");
        }
        if (screenshot.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new BadRequestException("Screenshot must be 5 MB or smaller.");
        }
        byte[] bytes;
        try {
            bytes = screenshot.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Screenshot could not be read. Please try again.");
        }
        if (bytes.length == 0 || !looksLikeImage(bytes, contentType)) {
            throw new BadRequestException("Screenshot must be a PNG, JPEG, or WebP image.");
        }
        feedback.setHasAttachment(true);
        feedback.setAttachmentFilename(sanitizeFilename(screenshot.getOriginalFilename()));
        feedback.setAttachmentMimeType(contentType);
        feedback.setAttachmentSizeBytes((long) bytes.length);
        return bytes;
    }

    /** Real content check (magic bytes) — never trust the client's extension or header alone. */
    private boolean looksLikeImage(byte[] bytes, String contentType) {
        return switch (contentType) {
            case "image/png" ->
                    bytes.length >= 8
                            && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P'
                            && bytes[2] == 'N' && bytes[3] == 'G';
            case "image/jpeg" ->
                    bytes.length >= 3
                            && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                            && (bytes[2] & 0xFF) == 0xFF;
            case "image/webp" ->
                    bytes.length >= 12
                            && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                            && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "screenshot";
        }
        // Drop any path components a client might have included.
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank()) {
            return "screenshot";
        }
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    /**
     * Notification attempt AFTER the save. Returns the honest outcome:
     * LOGGED when nothing is configured to notify (dev), SENT only on a real
     * SMTP success, FAILED when a configured send errored.
     */
    private NotificationStatus notifyTeam(Feedback feedback, User user, byte[] attachmentBytes,
                                          String attachmentType, String attachmentName) {
        String recipient = resolveRecipient();
        if (recipient == null) {
            log.info("Feedback {} saved; no notification recipient configured (LOGGED)",
                    reference(feedback));
            return NotificationStatus.LOGGED;
        }
        try {
            MailService.SendAttempt attempt = mailService.deliver(new MailService.OutboundMail(
                    recipient,
                    subject(feedback),
                    notificationHtml(feedback, user),
                    attachmentBytes != null ? attachmentName : null,
                    attachmentType,
                    attachmentBytes));
            return switch (attempt.outcome()) {
                case SENT -> NotificationStatus.SENT;
                case LOGGED -> NotificationStatus.LOGGED;
                case FAILED -> NotificationStatus.FAILED;
            };
        } catch (Exception ex) {
            // deliver() is not supposed to throw — treat any surprise as FAILED,
            // never as success, and never let it roll back the saved report.
            log.warn("Feedback notification for {} failed unexpectedly: {}",
                    reference(feedback), ex.getMessage());
            return NotificationStatus.FAILED;
        }
    }

    /**
     * Configured feedback inbox, falling back to the existing mail identity:
     * LC_FEEDBACK_RECIPIENT → lifeclues.mail.from → spring.mail.username.
     * Blank everywhere means "nothing to notify" (dev) — reported as LOGGED.
     */
    private String resolveRecipient() {
        String recipient = firstNonBlank(recipientConfig, mailFrom, smtpUsername);
        return recipient == null ? null : recipient.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String subject(Feedback feedback) {
        return "[LifeClues] " + categoryLabel(feedback.getCategory()) + ": "
                + feedback.getSubject() + " (" + reference(feedback) + ")";
    }

    private String reference(Feedback feedback) {
        return "LC-" + feedback.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    static String categoryLabel(FeedbackCategory category) {
        return switch (category) {
            case BUG -> "Bug Report";
            case SUGGESTION -> "Suggestion";
            case FEATURE_REQUEST -> "Feature Request";
            case GENERAL -> "General Feedback";
        };
    }

    /**
     * Notification body for the internal feedback inbox — intentionally plain
     * and backend-owned (EmailTemplates.java covers the user-facing
     * transactional emails and stays untouched).
     */
    private String notificationHtml(Feedback feedback, User user) {
        StringBuilder body = new StringBuilder();
        row(body, "Reference", reference(feedback));
        row(body, "Category", categoryLabel(feedback.getCategory()));
        row(body, "Subject", feedback.getSubject());
        row(body, "Submitted by", user.getUsername() + " (" + user.getEmail() + ")");
        if (feedback.getContactEmail() != null) {
            row(body, "Reply-to (provided)", feedback.getContactEmail());
        }
        row(body, "Created", feedback.getCreatedAt().toString());
        body.append("<p style=\"margin:18px 0 6px;font-family:Arial,sans-serif;font-size:12px;"
                + "letter-spacing:1.5px;text-transform:uppercase;color:#87806f;\">Description</p>")
                .append("<p style=\"margin:0 0 14px;font-family:Arial,sans-serif;font-size:14px;"
                        + "line-height:1.6;color:#211b11;white-space:pre-wrap;\">")
                .append(escape(feedback.getDescription())).append("</p>");
        if (feedback.getReproductionSteps() != null) {
            body.append("<p style=\"margin:0 0 6px;font-family:Arial,sans-serif;font-size:12px;"
                    + "letter-spacing:1.5px;text-transform:uppercase;color:#87806f;\">"
                    + "Reproduction steps</p>")
                    .append("<p style=\"margin:0 0 14px;font-family:Arial,sans-serif;font-size:14px;"
                            + "line-height:1.6;color:#211b11;white-space:pre-wrap;\">")
                    .append(escape(feedback.getReproductionSteps())).append("</p>");
        }
        if (feedback.isHasAttachment()) {
            row(body, "Screenshot", feedback.getAttachmentFilename()
                    + " (" + feedback.getAttachmentSizeBytes() + " bytes, "
                    + feedback.getAttachmentMimeType() + ")");
        } else {
            row(body, "Screenshot", "none");
        }
        return "<!DOCTYPE html><html><body style=\"margin:0;padding:20px;background:#f6f1e6;"
                + "font-family:Arial,Helvetica,sans-serif;\">"
                + "<div style=\"max-width:640px;margin:0 auto;background:#fffdf6;"
                + "border:1px solid #e7e0d0;border-radius:14px;padding:26px 28px;\">"
                + "<p style=\"margin:0 0 4px;font-size:11px;letter-spacing:2px;"
                + "text-transform:uppercase;color:#b4501e;\">LifeClues feedback</p>"
                + "<h1 style=\"margin:0 0 16px;font-size:21px;color:#211b11;\">"
                + escape(categoryLabel(feedback.getCategory())) + " received</h1>"
                + body
                + "<p style=\"margin:20px 0 0;font-size:12px;color:#87806f;\">Stored in the "
                + "LifeClues feedback queue.</p></div></body></html>";
    }

    private void row(StringBuilder body, String label, String value) {
        body.append("<p style=\"margin:0 0 4px;font-family:Arial,sans-serif;font-size:12px;"
                + "letter-spacing:1.5px;text-transform:uppercase;color:#87806f;\">")
                .append(escape(label)).append("</p>")
                .append("<p style=\"margin:0 0 12px;font-size:14px;line-height:1.5;color:#211b11;\">")
                .append(escape(value)).append("</p>");
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeContactEmail(String value) {
        String email = blankToNull(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }
}
