package com.ultimate.inventory.Service;

import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.UserRepository;
import com.ultimate.inventory.utils.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Makes sure the platform has a super admin. On the first start (or when asked to reset), it
 * creates one with a random password and writes the sign-in to a text file next to the app.
 * The password is stored hashed, so the file is the only place it can be read: keep it safe,
 * and never commit it (it is git-ignored).
 *
 *   app.superadmin.email            sign-in email (default superadmin@inventory.local)
 *   app.superadmin.credentials-file where to write it (default SUPER_ADMIN_CREDENTIALS.txt)
 *   app.superadmin.reset-password   true = issue a new password on this start (lost password)
 */
@Component
@Order(10) // after the demo data seeder, which only runs on an empty database
public class SuperAdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final UserRepository userRepo;
    private final Security security;

    @Value("${app.superadmin.email:superadmin@inventory.local}") private String email;
    @Value("${app.superadmin.credentials-file:SUPER_ADMIN_CREDENTIALS.txt}") private String file;
    @Value("${app.superadmin.reset-password:false}") private boolean reset;
    @Value("${app.frontend-url:http://localhost:3000}") private String frontendUrl;

    public SuperAdminBootstrap(UserRepository userRepo, Security security) {
        this.userRepo = userRepo;
        this.security = security;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        String e = email.trim().toLowerCase();
        User admin = userRepo.findBySuperAdminTrue().stream().findFirst().orElse(null);

        if (admin != null && !reset) {
            log.info("Super admin present ({}). Sign-in details were written to {} when it was created; " +
                    "start with --app.superadmin.reset-password=true to issue a new password.", admin.getEmail(), path());
            return;
        }

        if (admin == null) {
            User clash = userRepo.findByEmail(e);
            if (clash != null) {
                log.error("Cannot create the super admin: {} already belongs to a company user. " +
                        "Set app.superadmin.email to a different address.", e);
                return;
            }
            admin = new User();
            admin.setEmail(e);
            admin.setFirstName("Super");
            admin.setLastName("Admin");
            admin.setSuperAdmin(true);
            admin.setLocked(false);
            admin.setSuspended(false);
            admin.setDeleted(false);
            admin.setCreatedBy("system");
            admin.setCreatedAt(new Date());
        }

        // Longer than a hand-over temporary password: this one lives in a file.
        String password = UserAccounts.temporaryPassword() + "-" + UserAccounts.temporaryPassword().substring(0, 4);
        admin.setPassword(security.hashPassword(password));
        admin.setMustChangePassword(false); // the file stays valid until they choose to change it
        admin.setUpdatedAt(new Date());
        userRepo.save(admin);

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String text = """
                INVENTORY - SUPER ADMIN SIGN-IN
                ===============================

                Sign in at : %s/login
                Email      : %s
                Password   : %s

                Issued     : %s

                What this account does
                - Creates companies and each company's first admin (Admin console).
                - Adds more admins, resets an admin's password, suspends / reactivates companies.
                - Does not see inside companies' sales, stock or customers.

                Keep this file private. The password is stored hashed, so this file is the only
                copy. You can change the password after signing in (Profile). If it is lost,
                restart the backend with --app.superadmin.reset-password=true to get a new one
                written here.
                """.formatted(frontendUrl.replaceAll("/+$", ""), admin.getEmail(), password, now);
        Path p = path();
        Files.writeString(p, text, StandardCharsets.UTF_8);
        log.warn("Super admin {} {}. Sign-in written to {}", admin.getEmail(), reset ? "password reset" : "created", p);
    }

    private Path path() {
        return Path.of(file).toAbsolutePath().normalize();
    }
}
