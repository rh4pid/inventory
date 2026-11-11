package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "customer")
public class Customer extends Model {

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column
    private String email;

    @Column
    private String phone;

    @Column
    private String address;

    @Column(name = "loyalty_points")
    private Double loyaltyPoints = 0.0;

    @Column(name = "total_purchases")
    private Double totalPurchases = 0.0;

    private Boolean deleted = false;
}
