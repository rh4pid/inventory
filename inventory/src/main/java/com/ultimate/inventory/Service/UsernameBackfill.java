package com.ultimate.inventory.Service;

import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Date;

/**
 * Gives every account a valid username at start-up: accounts from before usernames get one made from
 * their name, and old usernames are lowercased (or replaced if they break the rules or clash).
 * Runs after the seeder and the super admin bootstrap, so the accounts they create are covered too.
 * Does nothing once every account is in order.
 */
@Component
@Order(100)
public class UsernameBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UsernameBackfill.class);

    private final UserRepository userRepo;
    private final Usernames usernames;

    public UsernameBackfill(UserRepository userRepo, Usernames usernames) {
        this.userRepo = userRepo;
        this.usernames = usernames;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int changed = 0;
        // In id order; an account whose username clashes with another's after lowercasing gets a new one.
        for (User u : userRepo.findAll().stream().sorted(Comparator.comparing(User::getId)).toList()) {
            String current = u.getUsername();
            String cleaned = Usernames.clean(current);
            String next = Usernames.problem(cleaned) == null && usernames.isFree(cleaned, u.getId())
                    ? cleaned
                    : usernames.suggest(u.getFirstName(), u.getLastName());
            if (next.equals(current)) continue;
            u.setUsername(next);
            u.setUpdatedAt(new Date());
            u.setUpdatedBy("system");
            userRepo.saveAndFlush(u); // flushed so the next account's clash check sees it
            changed++;
        }
        if (changed > 0) log.info("Gave {} account(s) a username", changed);
    }
}
