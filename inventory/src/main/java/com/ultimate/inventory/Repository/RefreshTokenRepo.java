package com.ultimate.inventory.Repository;


import com.ultimate.inventory.Model.RefreshTokens;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RefreshTokenRepo extends JpaRepository<RefreshTokens, Long> {
    RefreshTokens findAllByToken(String token);

    /** Signs a user out everywhere (e.g. after an administrator resets their password). */
    @org.springframework.transaction.annotation.Transactional
    void deleteByUser(com.ultimate.inventory.Model.User user);
}
