package com.ultimate.inventory.Model;

import com.ultimate.inventory.Constants.InvitationStatus;
import com.ultimate.inventory.Constants.UserRoles;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A company asking someone who already has an account to join, with a role and locations.
 * Nothing changes for the person until they accept.
 */
@Getter
@Setter
@Entity
@Table(name = "invitation", indexes = {
        @Index(name = "idx_invitation_invitee_status", columnList = "invitee_id,status"),
        @Index(name = "idx_invitation_business_status", columnList = "business_id,status")
})
public class Invitation extends Model {
    @ManyToOne(optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invitee_id", nullable = false)
    private User invitee;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invited_by_id", nullable = false)
    private User invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRoles role;

    /** Ids of the locations they'll work at, comma-separated. */
    @Column(name = "location_ids", length = 1000)
    private String locationIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Date expiresAt;

    @Column(name = "responded_at")
    private Date respondedAt;

    public List<Long> locationIdList() {
        if (locationIds == null || locationIds.isBlank()) return List.of();
        return Arrays.stream(locationIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
    }

    public void setLocationIdList(List<Long> ids) {
        this.locationIds = ids == null || ids.isEmpty() ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public boolean isOpen(Date now) {
        return status == InvitationStatus.PENDING && expiresAt.after(now);
    }
}
