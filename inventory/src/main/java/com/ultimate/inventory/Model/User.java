package com.ultimate.inventory.Model;

import com.ultimate.inventory.Constants.UserRoles;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "user")
public class User extends Model {

    @Column(name = "first_name")
    private String firstName;
    @Column(name = "other_name")
    private String otherName;
    @Column(name = "last_name")
    private String lastName;
    @Column(unique = true, nullable = false)
    private String email;
    @Column(name = "telephone_primary")
    private String telephonePrimary;
    @Column(name = "telephone_secondary")
    private String telephoneSecondary;
    @Column(name = "profile_picture")
    private String profilePicture;
    private String password;
    private String otp;
    @Column(name = "expiry_date")
    private Date expiryDate;
    private Boolean locked;
    private Boolean suspended;
    private Boolean deleted;
}
