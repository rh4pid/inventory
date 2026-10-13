package com.ultimate.inventory.Model;

import com.ultimate.inventory.Constants.UserRoles;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "shop_users")
public class ShopUsers extends Model {

    @ManyToOne
    @JoinColumn(name = "business_user")
    private BusinessUser businessUser;

    @ManyToOne
    @JoinColumn(name = "shop")
    private Shop shop;

    @Enumerated(EnumType.STRING)
    private UserRoles roles;

    private Boolean locked;
    private Boolean suspended;
    private Boolean deleted;
}
