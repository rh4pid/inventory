package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_permission")
public class UserPermission extends Model {

    @ManyToOne
    @JoinColumn(name = "business_user_id")
    private BusinessUser businessUser;

    @ManyToOne
    @JoinColumn(name = "permission")
    private Permission permission;

    private Boolean active;
    private Double maximum;
    private Double minimum;
}
