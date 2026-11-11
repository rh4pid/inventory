package com.ultimate.inventory.Model;

import com.ultimate.inventory.Constants.UserRoles;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "invitation_token")
public class InvitationToken extends Model {

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String email;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne
    @JoinColumn(name = "invited_by_id", nullable = false)
    private User invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRoles role;

    @Column(name = "expires_at", nullable = false)
    private Date expiresAt;

    @Column(nullable = false)
    private Boolean used = false;

    @Column(name = "used_at")
    private Date usedAt;
}
