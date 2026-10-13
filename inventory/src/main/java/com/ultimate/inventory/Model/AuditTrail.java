package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "audit_trail")
public class AuditTrail extends Model {
    @ManyToOne
    @JoinColumn(name = "user")
    private User user;
    private String action;
    @Column(name = "ip_address")
    private String ipAddress;
    private String detail;
}
