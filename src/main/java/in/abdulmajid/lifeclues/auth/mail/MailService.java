package in.abdulmajid.lifeclues.auth.mail;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends branded LifeClues emails (welcome / verification / password reset)
 * over SMTP, sharing one Paper &amp; Book template from {@link EmailTemplates}.
 * If SMTP is not configured, sending is silently skipped so the app still works.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String smtpUsername;
    private final String from;
    private final String configuredBaseUrl;
    private final EmailTemplates templates = new EmailTemplates();

    public MailService(JavaMailSender mailSender,
                       @Value("${spring.mail.username:}") String smtpUsername,
                       @Value("${lifeclues.mail.from:}") String from,
                       @Value("${lifeclues.app.base-url:}") String configuredBaseUrl) {
        this.mailSender = mailSender;
        this.smtpUsername = smtpUsername;
        this.from = from;
        this.configuredBaseUrl = configuredBaseUrl;
    }

    /** Welcome message with the verification link embedded, sent after registration. */
    public boolean sendWelcome(HttpServletRequest request, String to, String displayName,
                               String rawToken, long expiryMinutes) {
        String link = verificationLink(request, rawToken);
        String html = templates.welcome(to, displayName, link, validFor(expiryMinutes));
        return send(to, "Welcome to LifeClues — verify your email", html);
    }

    /** Standalone verification email, sent when a user requests a new link. */
    public boolean sendVerificationLink(HttpServletRequest request, String to, String rawToken,
                                        long expiryMinutes) {
        String link = verificationLink(request, rawToken);
        String html = templates.verify(to, link, validFor(expiryMinutes));
        return send(to, "Verify your email — LifeClues", html);
    }

    /** Password-reset email, sent when a user requests a reset. */
    public boolean sendResetLink(HttpServletRequest request, String to, String rawToken,
                                 long expiryMinutes) {
        String link = resetLink(request, rawToken);
        String html = templates.reset(to, link, validFor(expiryMinutes));
        return send(to, "Reset your password — LifeClues", html);
    }

    private String verificationLink(HttpServletRequest request, String rawToken) {
        return baseUrl(request) + "/verify-email?token=" + rawToken;
    }

    private String resetLink(HttpServletRequest request, String rawToken) {
        return baseUrl(request) + "/reset-password?token=" + rawToken;
    }

    private String validFor(long minutes) {
        if (minutes <= 0) {
            return "a short time";
        }
        if (minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + " minutes";
    }

    private boolean send(String to, String subject, String html) {
        if (!isConfigured()) {
            log.info("SMTP not configured; skipping email to {}", to);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from.isBlank() ? smtpUsername : from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Sent '{}' to {}", subject, to);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to send '{}' to {}: {}", subject, to, ex.getMessage());
            return false;
        }
    }

    private boolean isConfigured() {
        return smtpUsername != null && !smtpUsername.isBlank();
    }

    /**
     * The base URL for emailed links: an explicit LC_APP_BASE_URL wins; otherwise
     * it is detected from the incoming request so localhost links work locally
     * and the hosted domain works once deployed. HTTPS is respected when the
     * proxy forwards X-Forwarded-Proto.
     */
    private String baseUrl(HttpServletRequest request) {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl.replaceFirst("/+$", "");
        }
        // Standard reverse proxies (nginx, etc.) forward these headers; trust them when present.
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedHost != null && !forwardedHost.isBlank()
                && forwardedProto != null && !forwardedProto.isBlank()) {
            return (forwardedProto.trim().startsWith("https") ? "https" : "http")
                    + "://" + forwardedHost.trim();
        }
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            host = "localhost:5173";
        }
        // localhost = plain http; any other (public) hostname is served over HTTPS.
        boolean secure = !(host.startsWith("localhost") || host.startsWith("127.0.0.1"));
        return (secure ? "https" : "http") + "://" + host;
    }
}