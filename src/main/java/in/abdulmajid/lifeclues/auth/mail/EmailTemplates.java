package in.abdulmajid.lifeclues.auth.mail;

import java.time.Year;
import java.util.List;

/**
 * Builds the branded LifeClues email HTML shared by the welcome, verification
 * and password-reset emails. One "Paper &amp; Book" identity: warm paper card,
 * Space Grotesk body text, IBM Plex Mono eyebrows, Caveat headings, sienna
 * accents, indigo pill buttons and the Small Clues. Big Memories. tagline.
 */
final class EmailTemplates {

    private static final String PAPER = "#f6f1e6";
    private static final String SURFACE = "#fffdf6";
    private static final String LINE = "#e7e0d0";
    private static final String INK = "#211b11";
    private static final String INK_SOFT = "#6e675a";
    private static final String INK_FAINT = "#87806f";
    private static final String ACCENT = "#6e6d8e";
    private static final String SIENNA = "#b4501e";
    private static final String LOGO_BG = "#4a3728";
    private static final String LOGO_FG = "#fff7ea";

    private static final String SANS = "'Space Grotesk','Segoe UI',Arial,Helvetica,sans-serif";
    private static final String MONO = "'IBM Plex Mono','Courier New',monospace";
    private static final String HAND = "'Caveat','Segoe Script','Comic Sans MS',cursive";

    /** Welcome + embedded verification link, sent right after registration. */
    String welcome(String recipient, String firstName, String link, String validFor) {
        String name = firstName == null || firstName.isBlank() ? "friend" : firstName.trim();
        int space = name.indexOf(' ');
        if (space > 0) {
            name = name.substring(0, space);
        }
        return render(new Content(
                "Welcome to LifeClues. Confirm your email and start keeping your memory book.",
                "Welcome to LifeClues",
                "Welcome, " + name + "!",
                List.of(
                        "LifeClues is where tiny moments become memories that stay with you. "
                                + "Write a small clue today, and it quietly keeps the whole story safe for tomorrow.",
                        "To begin, please confirm that this email address is yours."),
                "Verify my email",
                link,
                "This verification link works for the next " + validFor
                        + ". You'll only need to do this once.",
                recipient,
                "You're receiving this because a LifeClues account was created with this email "
                        + "address. If that wasn't you, you can safely ignore this message."));
    }

    /** Standalone verification email, sent when a new link is requested. */
    String verify(String recipient, String link, String validFor) {
        return render(new Content(
                "Confirm this email address is yours so you can sign in to LifeClues.",
                "Verify your email",
                "Confirm this email is yours",
                List.of(
                        "One quick step and you're all set — confirming your email keeps your "
                                + "LifeClues book private and secure.",
                        "Tap the button below to verify this address and finish setting up your account."),
                "Verify my email",
                link,
                "This link is valid for the next " + validFor
                        + ". If you didn't request it, you can safely ignore this email.",
                recipient,
                "You're receiving this because a verification link was requested for this "
                        + "LifeClues account."));
    }

    /** Password-reset email, sent when a reset is requested. */
    String reset(String recipient, String link, String validFor) {
        return render(new Content(
                "Someone asked to reset the password for your LifeClues account.",
                "Reset your password",
                "Let's set a new password",
                List.of(
                        "We received a request to reset the password for your LifeClues account.",
                        "Tap the button below to choose a new one. If you didn't ask for this, "
                                + "no action is needed — your current password stays the same."),
                "Reset my password",
                link,
                "This link is valid for the next " + validFor
                        + " and is single-use, so it stops working once you've set your new password.",
                recipient,
                "You're receiving this because a password reset was requested for this "
                        + "LifeClues account."));
    }

    private record Content(String preheader, String eyebrow, String heading,
                           List<String> paragraphs, String ctaLabel, String ctaUrl,
                           String note, String recipient, String blurb) {
    }

    private String render(Content c) {
        String eyebrow = c.eyebrow().toUpperCase();
        StringBuilder body = new StringBuilder();
        body.append("<p style=\"margin:0 0 15px;\">")
                .append(c.paragraphs().get(0))
                .append("</p>");
        for (int i = 1; i < c.paragraphs().size(); i++) {
            body.append("<p style=\"margin:0 0 15px;\">")
                    .append(c.paragraphs().get(i))
                    .append("</p>");
        }
        body.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"margin:8px 0 4px;\"><tr><td align=\"center\" "
                + "style=\"border-radius:999px;background-color:").append(ACCENT).append(";\">")
                .append("<a href=\"").append(c.ctaUrl()).append("\" "
                        + "style=\"display:inline-block;padding:15px 34px;font-family:").append(SANS)
                .append(";font-size:15px;font-weight:600;line-height:16px;color:#ffffff;"
                        + "text-decoration:none;border-radius:999px;mso-padding-alt:15px 34px;\">")
                .append(c.ctaLabel()).append("</a></td></tr></table>");
        body.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "border=\"0\" style=\"margin:22px 0 0;\"><tr><td>")
                .append("<p style=\"margin:0 0 6px;font-family:").append(MONO)
                .append(";font-size:11px;color:").append(INK_FAINT).append(";\">Or copy this link "
                        + "into your browser:</p>")
                .append("<p style=\"margin:0;padding:11px 14px;border:1px dashed ").append(LINE)
                .append(";border-radius:12px;background-color:#faf6ea;font-family:").append(MONO)
                .append(";font-size:12px;line-height:1.5;color:").append(INK_SOFT)
                .append(";word-break:break-all;\">").append(escape(c.ctaUrl())).append("</p></td></tr></table>");
        body.append("<p style=\"margin:22px 0 0;font-family:").append(SANS)
                .append(";font-size:13px;line-height:1.6;color:").append(INK_FAINT).append(";\">")
                .append(c.note()).append("</p>");

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\" xmlns=\"http://www.w3.org/1999/xhtml\">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n"
                + "  <meta name=\"x-apple-disable-message-reformatting\">\n"
                + "  <title>" + escape(c.preheader()) + "</title>\n"
                + headFallback()
                + "</head>\n"
                + "<body style=\"margin:0;padding:0;background-color:" + PAPER + ";"
                + "-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;\">\n"
                + "  <div style=\"display:none;max-height:0;overflow:hidden;mso-hide:all;opacity:0;\">"
                + escape(c.preheader()) + "&nbsp;&zwnj;</div>\n"
                + "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "border=\"0\" style=\"background-color:" + PAPER + ";padding:28px 16px 44px;\">\n"
                + "    <tr>\n"
                + "      <td align=\"center\">\n"
                + "        <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "border=\"0\" style=\"max-width:600px;width:600px;background-color:" + SURFACE + ";"
                + "border:1px solid " + LINE + ";border-radius:18px;overflow:hidden;\">\n"
                + "          <tr>\n"
                + "            <td style=\"height:4px;font-size:0;line-height:0;background-color:" + SIENNA + ";\">&nbsp;</td>\n"
                + "          </tr>\n"
                + headerLockup()
                + "          <tr>\n"
                + "            <td style=\"padding:34px 32px 6px;\">\n"
                + "              <p style=\"margin:0;font-family:" + MONO + ";font-size:10px;letter-spacing:3px;"
                + "text-transform:uppercase;color:" + SIENNA + ";\">" + escape(eyebrow) + "</p>\n"
                + "              <h1 style=\"margin:10px 0 0;font-family:" + HAND + ";font-size:34px;font-weight:600;"
                + "line-height:1.1;color:" + INK + ";\">" + escape(c.heading()) + "</h1>\n"
                + underline()
                + "              <div style=\"margin:18px 0 0;font-family:" + SANS + ";font-size:15px;line-height:1.65;"
                + "color:" + INK_SOFT + ";\">\n" + body + "\n</div>\n"
                + "            </td>\n"
                + "          </tr>\n"
                + footer(c)
                + "        </table>\n"
                + "      </td>\n"
                + "    </tr>\n"
                + "  </table>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private String headFallback() {
        return "  <!--[if mso]>\n"
                + "  <style>*{border:0;margin:0;padding:0}table{border-collapse:collapse}td,div,a{line-height:1.5}</style>\n"
                + "  <![endif]-->\n";
    }

    private String headerLockup() {
        return "          <tr>\n"
                + "            <td style=\"padding:26px 32px 20px;border-bottom:1px solid " + LINE + ";\">\n"
                + "              <table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n"
                + "                <tr>\n"
                + "                  <td style=\"width:42px;height:42px;background-color:" + LOGO_BG + ";"
                + "border-radius:12px;text-align:center;vertical-align:middle;\">\n"
                + "                    <svg width=\"22\" height=\"22\" viewBox=\"0 0 24 24\" fill=\"none\" "
                + "xmlns=\"http://www.w3.org/2000/svg\" style=\"display:block;margin:auto;\">\n"
                + "                      <path d=\"M12 21C8 21 5 18.2 5 14.2 5 9.6 8.1 6 12 6s7 3.6 7 8.2c0 4-3 6.8-7 6.8Z\" "
                + "stroke=\"" + LOGO_FG + "\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n"
                + "                      <path d=\"M12 6c3 1.6 5 4.8 5.6 8.4\" stroke=\"" + LOGO_FG
                + "\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n"
                + "                      <path d=\"M12 6c-1.6 1.8-2.6 4.2-2.9 6.8\" stroke=\"" + LOGO_FG
                + "\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n"
                + "                      <path d=\"M4.5 19c4-.5 7.6-2.4 10-5.5\" stroke=\"" + LOGO_FG
                + "\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\" opacity=\"0.6\"/>\n"
                + "                    </svg>\n"
                + "                  </td>\n"
                + "                  <td style=\"padding-left:12px;vertical-align:middle;\">\n"
                + "                    <div style=\"font-family:" + SANS + ";font-size:19px;font-weight:700;"
                + "letter-spacing:-0.3px;color:#2d251d;line-height:1;\">LifeClues</div>\n"
                + "                    <div style=\"margin-top:3px;font-family:" + MONO + ";font-size:9px;"
                + "letter-spacing:2.5px;text-transform:uppercase;color:" + INK_FAINT + ";line-height:1;\">"
                + "Small Clues &middot; Big Memories</div>\n"
                + "                  </td>\n"
                + "                </tr>\n"
                + "              </table>\n"
                + "            </td>\n"
                + "          </tr>\n";
    }

    private String underline() {
        return "              <svg width=\"176\" height=\"14\" viewBox=\"0 0 260 18\" preserveAspectRatio=\"none\" "
                + "fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\" style=\"display:block;margin:4px 0 0;\">\n"
                + "                <path d=\"M5 12c30-6 62-9 96-7 34 2 78 8 116 3 15-2 28-4 39-4\" stroke=\"" + SIENNA
                + "\" stroke-width=\"6\" stroke-linecap=\"round\" opacity=\"0.85\"/>\n"
                + "              </svg>\n";
    }

    private String footer(Content c) {
        return "          <tr>\n"
                + "            <td style=\"padding:26px 32px 30px;border-top:1px solid " + LINE
                + ";background-color:#faf6ea;border-radius:0 0 18px 18px;text-align:center;\">\n"
                + "              <p style=\"margin:0 0 6px;font-family:" + MONO + ";font-size:9px;letter-spacing:3px;"
                + "text-transform:uppercase;color:" + INK_FAINT + ";\">Small Clues &middot; Big Memories</p>\n"
                + "              <p style=\"margin:0 0 14px;font-family:" + SANS + ";font-size:11px;color:" + INK_SOFT
                + ";\">A private journal &middot; just you</p>\n"
                + "              <p style=\"margin:0 0 6px;font-family:" + SANS + ";font-size:11px;line-height:1.6;"
                + "color:" + INK_FAINT + ";\">" + escape(c.blurb()) + "</p>\n"
                + "              <p style=\"margin:0;font-family:" + SANS + ";font-size:11px;color:" + INK_FAINT
                + ";\">This email was sent to " + escape(c.recipient()) + "</p>\n"
                + "              <p style=\"margin:10px 0 0;font-family:" + SANS + ";font-size:11px;color:" + INK_FAINT
                + ";\">&copy; " + Year.now().getValue() + " LifeClues</p>\n"
                + "            </td>\n"
                + "          </tr>\n";
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}