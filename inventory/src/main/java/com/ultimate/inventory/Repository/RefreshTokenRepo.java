package com.ultimate.inventory.Repository;


import com.ultimate.inventory.Model.RefreshTokens;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RefreshTokenRepo extends JpaRepository<RefreshTokens, Long> {
    RefreshTokens findAllByToken(String token);
}
