package com.ultimate.inventory.Model;

import com.ultimate.inventory.Constants.UserRoles;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "business_user")
public class BusinessUser extends Model {

    @ManyToOne
    @JoinColumn(name = "user")
    private User user;

    @ManyToOne
    @JoinColumn(name = "business")
    private Business business;

    @Enumerated(EnumType.STRING)
    private UserRoles roles;
}
