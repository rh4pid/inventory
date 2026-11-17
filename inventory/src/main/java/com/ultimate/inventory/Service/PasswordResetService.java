package com.ultimate.inventory.Service;

import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.RefreshTokenRepo;
import com.ultimate.inventory.Repository.UserRepository;
import com.ultimate.inventory.utils.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * "Forgot password": we email a one-time link (valid 30 minutes) from the site's own address.
 * Only a hash of the link is stored, and the answer never says whether an account exists.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    static final long VALID_MS = 30L * 60 * 1000;
    /** At most one email per account per minute. */
    static final long RESEND_GAP_MS = 60L * 1000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepo;
    private final RefreshTokenRepo refreshTokenRepo;
    private final Security security;
    private final Mailer mailer;

    @Value("${app.frontend-url:http://localhost:3000}") private String frontendUrl;
    @Value("${app.name:Inventory}") private String appName;

    public PasswordResetService(UserRepository userRepo, RefreshTokenRepo refreshTokenRepo, Security security, Mailer mailer) {
        this.userRepo = userRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.security = security;
        this.mailer = mailer;
    }

    /** Emails a reset link if the email or username belongs to an account. Says nothing either way. */
    @Transactional
    public void requestReset(String login) {
        User user = find(login);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            log.info("Password reset asked for an unknown account");
            return;
        }
        Date now = new Date();
        if (user.getPasswordResetSentAt() != null && now.getTime() - user.getPasswordResetSentAt().getTime() < RESEND_GAP_MS) {
            return;
        }
        String token = newToken();
        user.setPasswordResetHash(sha256(token));
        user.setPasswordResetExpires(new Date(now.getTime() + VALID_MS));
        user.setPasswordResetSentAt(now);
        userRepo.save(user);

        String link = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + token;
        String name = user.getFirstName() != null && !user.getFirstName().isBlank() ? user.getFirstName().trim() : "there";
        mailer.send(user.getEmail(), "Reset your " + appName + " password", html(name, link), text(name, link));
        log.info("Password reset link issued for user {}", user.getId());
    }

    /** Sets a new password from a reset link, then signs the person out everywhere. */
    @Transactional
    public void reset(String token, String newPassword) {
        if (token == null || token.isBlank()) throw invalid();
        User user = userRepo.findByPasswordResetHash(sha256(token.trim()));
        if (user == null || user.getPasswordResetExpires() == null || !user.getPasswordResetExpires().after(new Date())
                || Boolean.TRUE.equals(user.getDeleted())) {
            throw invalid();
        }
        UserAccounts.checkNewPassword(newPassword);
        user.setPassword(security.hashPassword(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordResetHash(null);
        user.setPasswordResetExpires(null);
        user.setUpdatedBy(user.getEmail());
        user.setUpdatedAt(new Date());
        userRepo.save(user);
        refreshTokenRepo.deleteByUser(user);
        log.info("Password reset completed for user {}", user.getId());
    }

    private User find(String login) {
        String l = login == null ? "" : login.trim();
        if (l.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Enter your email or username");
        if (l.indexOf('@') > 0) {
            User exact = userRepo.findByEmail(l.toLowerCase());
            return exact != null ? exact : userRepo.findFirstByEmailIgnoreCaseOrderByIdAsc(l);
        }
        String username = Usernames.clean(l);
        return username.isEmpty() ? null : userRepo.findByUsername(username);
    }

    private static AppException invalid() {
        return new AppException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has expired. Ask for a new one.");
    }

    private static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String text(String name, String link) {
        return """
                Hi %s,

                Someone (hopefully you) asked to reset the password for your %s account.
                Open this link to choose a new password. It works once and expires in 30 minutes:

                %s

                If you didn't ask for this, ignore this email; your password stays the same.

                The %s team""".formatted(name, appName, link, appName);
    }

    private String html(String name, String link) {
        String safeName = escape(name);
        String safeApp = escape(appName);
        return """
                <!doctype html><html><body style="margin:0;background:#f4f6fa;font-family:Arial,Helvetica,sans-serif;color:#1d2433">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:32px 16px">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:16px;padding:32px">
                <tr><td>
                <p style="margin:0 0 4px;font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:#6b7385">%s</p>
                <h1 style="margin:0 0 16px;font-size:22px">Reset your password</h1>
                <p style="margin:0 0 16px;line-height:1.5">Hi %s, someone (hopefully you) asked to reset the password for your %s account.</p>
                <p style="margin:24px 0"><a href="%s" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:10px;font-weight:bold">Choose a new password</a></p>
                <p style="margin:0 0 16px;line-height:1.5;font-size:14px;color:#6b7385">The link works once and expires in 30 minutes. If you didn't ask for this, ignore this email; your password stays the same.</p>
                <p style="margin:0;font-size:12px;color:#9aa1b1;word-break:break-all">%s</p>
                </td></tr></table>
                </td></tr></table></body></html>""".formatted(safeApp, safeName, safeApp, link, link);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
