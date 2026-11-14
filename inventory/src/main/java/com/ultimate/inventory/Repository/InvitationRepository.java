package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Constants.InvitationStatus;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Invitation;
import com.ultimate.inventory.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    List<Invitation> findByInviteeAndStatusOrderByCreatedAtDesc(User invitee, InvitationStatus status);
    List<Invitation> findByBusinessAndStatusOrderByCreatedAtDesc(Business business, InvitationStatus status);
    Invitation findFirstByBusinessAndInviteeAndStatus(Business business, User invitee, InvitationStatus status);
}
