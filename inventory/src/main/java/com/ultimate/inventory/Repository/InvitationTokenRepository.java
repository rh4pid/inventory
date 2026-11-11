package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.InvitationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvitationTokenRepository extends JpaRepository<InvitationToken, Long> {
    Optional<InvitationToken> findByTokenAndUsedFalse(String token);
}
