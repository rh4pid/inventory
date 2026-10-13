package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
@Entity
@Table(name = "refresh_tokens",
        indexes = {@Index(name = "user_tide", columnList = "token, ipAddress, deviceInfo")}
)
public class RefreshTokens extends Model {

    @ManyToOne
    private User user;
    @Column(unique = true)
    private String token;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "expiry_date")
    private Date expiryDate;

}
