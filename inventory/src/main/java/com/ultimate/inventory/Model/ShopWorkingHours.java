package com.ultimate.inventory.Model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name="shop_working_hours")
public class ShopWorkingHours extends Model {

    @ManyToOne
    @JoinColumn(name = "shop")
    private Shop shop;
    private String day;
    @Column(name = "opening_time")
    private Date openingTime;
    @Column(name = "closing_time")
    private Date closingTime;
}
