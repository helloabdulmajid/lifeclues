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
 * Sends LifeClues emails (verification / password reset) over SMTP.
 * If SMTP is not configured, sending is silently skipped so the app still works.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String smtpUsername;
    private final String from;
    private final String configuredBaseUrl;

    public MailService(JavaMailSender mailSender,
                       @Value("${spring.mail.username:}") String smtpUsername,
                       @Value("${lifeclues.mail.from:}") String from,
                       @Value("${lifeclues.app.base-url:}") String configuredBaseUrl) {
        this.mailSender = mailSender;
        this.smtpUsername = smtpUsername;
        this.from = from;
        this.configuredBaseUrl = configuredBaseUrl;
    }

    public boolean sendVerificationLink(HttpServletRequest request, String to, String rawToken) {
        String link = baseUrl(request) + "/verify-email?token=" + rawToken;
        log.debug("Verification link for {}: {}", to, link);
        String body = """
                <p>Hello,</p>
                <p>Welcome to LifeClues! Confirm that this email address is yours so you can start
                keeping your memory book.</p>
                <p><a href="%s" style="display:inline-block;background:#8a5a44;color:#fff;padding:10px 22px;border-radius:10px;text-decoration:none;">Verify my email</a></p>
                <p>Or copy this link into your browser: <a href="%s">%s</a></p>
                <p>If you did not create a LifeClues account, you can simply ignore this email.</p>
                <p>— LifeClues</p>
                """.formatted(link, link, link);
        return send(to, "Verify your email — LifeClues", body);
    }

    public boolean sendResetLink(HttpServletRequest request, String to, String rawToken) {
        String link = baseUrl(request) + "/reset-password?token=" + rawToken;
        log.debug("Reset link for {}: {}", to, link);
        String body = """
                <p>Hello,</p>
                <p>Someone asked to reset the password for your LifeClues account. If that was you,
                click below to choose a new password:</p>
                <p><a href="%s" style="display:inline-block;background:#8a5a44;color:#fff;padding:10px 22px;border-radius:10px;text-decoration:none;">Reset my password</a></p>
                <p>Or copy this link into your browser: <a href="%s">%s</a></p>
                <p>This link works for 30 minutes. If you did not ask for it, you can safely ignore
                this email — your password stays the same.</p>
                <p>— LifeClues</p>
                """.formatted(link, link, link);
        return send(to, "Reset your password — LifeClues", body);
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