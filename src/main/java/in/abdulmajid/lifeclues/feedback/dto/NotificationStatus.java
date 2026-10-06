package in.abdulmajid.lifeclues.feedback.dto;

/**
 * Honest outcome of the notification attempt for a feedback report.
 * SENT only when a real SMTP send succeeded; LOGGED when SMTP is not
 * configured (development/fallback) so the UI never claims delivery;
 * FAILED when a configured send was attempted and failed.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    LOGGED,
    FAILED
}
