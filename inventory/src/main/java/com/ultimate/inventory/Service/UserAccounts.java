package com.ultimate.inventory.Service;

import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.RefreshTokenRepo;
import com.ultimate.inventory.Repository.UserRepository;
import com.ultimate.inventory.utils.Security;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Date;

/**
 * Creating sign-ins for other people. There is no email sending, so whoever creates an account
 * gets a temporary password to hand over; the person must replace it at first sign-in.
 */
@Component
public class UserAccounts {

    /** No 0/O, 1/l/I: temporary passwords are read out loud or typed from a message. */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepo;
    private final RefreshTokenRepo refreshTokenRepo;
    private final Security security;
    private final Usernames usernames;

    /**
     * Testing aid: when set (app.temporary-password), every new account and every reset gets this
     * password instead of a random one. Leave it unset in production.
     */
    private final String fixedTemporaryPassword;

    public UserAccounts(UserRepository userRepo, RefreshTokenRepo refreshTokenRepo, Security security, Usernames usernames,
                        @org.springframework.beans.factory.annotation.Value("${app.temporary-password:}") String fixedTemporaryPassword) {
        this.userRepo = userRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.security = security;
        this.usernames = usernames;
        this.fixedTemporaryPassword = fixedTemporaryPassword == null ? "" : fixedTemporaryPassword.trim();
        if (!this.fixedTemporaryPassword.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(UserAccounts.class).warn(
                    "app.temporary-password is set: new accounts and password resets all get the same temporary password. " +
                    "Remove it before going live.");
        }
    }

    /** The temporary password to hand out: the fixed testing one if configured, otherwise random. */
    private String issueTemporaryPassword() {
        return fixedTemporaryPassword.isEmpty() ? temporaryPassword() : fixedTemporaryPassword;
    }

    /** The account, and its temporary password if it was just created (null for an existing account). */
    public record Provisioned(User user, String temporaryPassword, boolean existing) {}

    /** e.g. "Hk7m-Px3r-Tz9w" (about 69 bits). */
    public static String temporaryPassword() {
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < 3; g++) {
            if (g > 0) sb.append('-');
            for (int i = 0; i < 4; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, "Email is required");
        String e = email.trim().toLowerCase();
        if (!e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new AppException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        return e;
    }

    /**
     * Finds the person's existing sign-in by email, or creates one with a temporary password.
     * An existing account keeps its own password (it may already be used for another company).
     */
    @Transactional
    public Provisioned findOrCreate(String firstName, String lastName, String email, String phone, String createdBy) {
        String e = normalizeEmail(email);
        User existing = userRepo.findByEmail(e);
        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getSuperAdmin())) {
                throw new AppException(HttpStatus.CONFLICT, "That email belongs to the system administrator");
            }
            if (Boolean.TRUE.equals(existing.getDeleted())) {
                throw new AppException(HttpStatus.CONFLICT, "That email belongs to a deleted account");
            }
            return new Provisioned(existing, null, true);
        }
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "First and last name are required");
        }
        String temp = issueTemporaryPassword();
        User u = new User();
        u.setEmail(e);
        u.setFirstName(firstName.trim());
        u.setLastName(lastName.trim());
        // Made from their name; they can change it in their profile.
        u.setUsername(usernames.suggest(u.getFirstName(), u.getLastName()));
        if (phone != null && !phone.isBlank()) u.setTelephonePrimary(phone.trim());
        u.setPassword(security.hashPassword(temp));
        u.setMustChangePassword(true);
        u.setSuperAdmin(false);
        u.setLocked(false);
        u.setSuspended(false);
        u.setDeleted(false);
        u.setCreatedBy(createdBy);
        u.setCreatedAt(new Date());
        return new Provisioned(userRepo.save(u), temp, false);
    }

    /** Replaces the password with a new temporary one and signs the person out everywhere. */
    @Transactional
    public String resetToTemporary(User user, String by) {
        String temp = issueTemporaryPassword();
        user.setPassword(security.hashPassword(temp));
        user.setMustChangePassword(true);
        user.setUpdatedBy(by);
        user.setUpdatedAt(new Date());
        userRepo.save(user);
        refreshTokenRepo.deleteByUser(user);
        return temp;
    }

    /** Rules for a password someone chooses for themselves. */
    public static void checkNewPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Use at least 8 characters");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Use both letters and numbers");
        }
    }

    public static String displayName(User u) {
        String n = ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim();
        return n.isEmpty() ? u.getEmail() : n;
    }
}
