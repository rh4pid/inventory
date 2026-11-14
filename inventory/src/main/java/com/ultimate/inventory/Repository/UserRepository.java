package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    /** Usernames are stored lowercase (see Usernames), so pass a cleaned one. */
    User findByUsername(String username);
    /** Older accounts may have mixed-case emails. */
    User findFirstByEmailIgnoreCaseOrderByIdAsc(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findBySuperAdminTrue();
}
