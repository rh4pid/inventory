package com.ultimate.inventory.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @Column(unique = true)
    private String username;
    @Column(name = "telephone_primary")
    private String telephonePrimary;
    @Column(name = "telephone_secondary")
    private String telephoneSecondary;
    @Column(name = "profile_picture")
    private String profilePicture;
    // Never sent to clients (the User entity is nested in many responses).
    @JsonIgnore
    private String password;
    @JsonIgnore
    private String otp;
    @JsonIgnore
    @Column(name = "expiry_date")
    private Date expiryDate;
    private Boolean locked;
    private Boolean suspended;
    private Boolean deleted;

    /** Platform operator: creates companies and their admins; not a member of any company. */
    @JsonIgnore
    @Column(name = "super_admin")
    private Boolean superAdmin = false;

    /** Set when someone else chose the password (a temporary one); cleared when the user sets their own. */
    @JsonIgnore
    @Column(name = "must_change_password")
    private Boolean mustChangePassword = false;
}
