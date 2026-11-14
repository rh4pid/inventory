package com.ultimate.inventory.Service;

import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Usernames are a person's handle across every company (like @handles in a chat app): others find
 * and invite them by it, and they can sign in with it. Stored lowercase, so "@Ama" and "@ama" are the same.
 */
@Component
public class Usernames {

    public static final int MIN = 3;
    public static final int MAX = 20;
    private static final Pattern RULE = Pattern.compile("^[a-z0-9_]{" + MIN + "," + MAX + "}$");

    /** Names that would look official or clash with app words. */
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "system", "support", "help", "api", "me", "you",
            "settings", "login", "logout", "register", "signup", "signin", "inventory",
            "owner", "staff", "superadmin", "super_admin", "null", "undefined");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepo;

    public Usernames(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /** What people type, tidied: spaces trimmed, a leading @ dropped, lowercase. */
    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("@")) s = s.substring(1);
        return s.toLowerCase(Locale.ROOT);
    }

    /** Why this can't be a username, or null if it can (ignoring whether someone already has it). */
    public static String problem(String cleaned) {
        if (cleaned.length() < MIN || cleaned.length() > MAX) {
            return "Usernames are " + MIN + " to " + MAX + " characters";
        }
        if (!RULE.matcher(cleaned).matches()) {
            return "Use only letters, numbers and _";
        }
        if (RESERVED.contains(cleaned)) {
            return "That username is reserved";
        }
        return null;
    }

    /** True if nobody but {@code exceptUserId} (may be null) has this cleaned username. */
    public boolean isFree(String cleaned, Long exceptUserId) {
        User owner = userRepo.findByUsername(cleaned);
        return owner == null || Objects.equals(owner.getId(), exceptUserId);
    }

    /** The cleaned username, if it follows the rules and is free; otherwise a 400/409 saying why. */
    public String requireAvailable(String raw, Long exceptUserId) {
        String u = clean(raw);
        String problem = problem(u);
        if (problem != null) throw new AppException(HttpStatus.BAD_REQUEST, problem);
        if (!isFree(u, exceptUserId)) throw new AppException(HttpStatus.CONFLICT, "@" + u + " is taken");
        return u;
    }

    /** A free username from the person's name: ama_mensah, then ama_mensah2, ama_mensah3… */
    public String suggest(String firstName, String lastName) {
        String first = slug(firstName);
        String last = slug(lastName);
        String base = first.isEmpty() ? last : last.isEmpty() ? first : first + "_" + last;
        if (base.length() < MIN) base = (base + "_user").replaceAll("^_", "");
        // Leave room for a number on the end.
        if (base.length() > MAX - 3) base = base.substring(0, MAX - 3).replaceAll("_+$", "");

        if (problem(base) == null && isFree(base, null)) return base;
        for (int n = 2; n < 1000; n++) {
            String candidate = base + n;
            if (problem(candidate) == null && isFree(candidate, null)) return candidate;
        }
        while (true) {
            String candidate = base + "_" + (1000 + RANDOM.nextInt(9000));
            if (candidate.length() > MAX) candidate = candidate.substring(candidate.length() - MAX);
            if (problem(candidate) == null && isFree(candidate, null)) return candidate;
        }
    }

    /** "Ámà-Kúsí O'Neil" -> "ama_kusi_oneil". */
    private static String slug(String s) {
        if (s == null) return "";
        String plain = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
