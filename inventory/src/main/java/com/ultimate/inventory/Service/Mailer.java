package com.ultimate.inventory.Service;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emails sent by the platform itself (password resets and the like), always from the site's own
 * address (app.mail.from), never from a company's address.
 *
 * Sent over HTTPS to an email API, because Render's free plan blocks SMTP ports:
 *   MAIL_PROVIDER=resend  + MAIL_API_KEY  (resend.com, needs a verified domain)
 *   MAIL_PROVIDER=brevo   + MAIL_API_KEY  (brevo.com, can send from a single verified address)
 * With no API key the email is written to the log instead, so local testing works without an account.
 */
@Service
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);
    private static final Pattern NAMED = Pattern.compile("^\\s*(.*?)\\s*<\\s*([^>]+)\\s*>\\s*$");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Value("${app.mail.provider:}") private String provider;
    @Value("${app.mail.api-key:}") private String apiKey;
    @Value("${app.mail.from:Inventory <no-reply@example.com>}") private String from;

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Sends one email. Returns false (and logs why) if it could not be sent; never throws. */
    public boolean send(String to, String subject, String html, String text) {
        if (!configured()) {
            log.warn("""
                    Email not sent (no MAIL_API_KEY set). It would have gone to {}:
                    Subject: {}
                    {}""", to, subject, text);
            return false;
        }
        try {
            HttpRequest request = "brevo".equalsIgnoreCase(provider.trim()) ? brevo(to, subject, html, text) : resend(to, subject, html, text);
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 == 2) return true;
            log.error("Email to {} was refused ({}): {}", to, res.statusCode(), res.body());
        } catch (Exception e) {
            log.error("Email to {} could not be sent: {}", to, e.toString());
        }
        return false;
    }

    private HttpRequest resend(String to, String subject, String html, String text) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", List.of(to));
        body.put("subject", subject);
        body.put("html", html);
        body.put("text", text);
        return HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
    }

    private HttpRequest brevo(String to, String subject, String html, String text) throws Exception {
        Matcher m = NAMED.matcher(from);
        Map<String, Object> sender = m.matches() ? Map.of("name", m.group(1), "email", m.group(2)) : Map.of("email", from.trim());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", List.of(Map.of("email", to)));
        body.put("subject", subject);
        body.put("htmlContent", html);
        body.put("textContent", text);
        return HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                .timeout(Duration.ofSeconds(20))
                .header("api-key", apiKey.trim())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
    }
}
