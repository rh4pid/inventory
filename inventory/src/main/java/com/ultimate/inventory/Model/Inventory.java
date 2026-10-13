package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "inventory")
public class Inventory extends Model {

    @ManyToOne
    @JoinColumn(name = "product")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "shop")
    private Shop shop;

    @Column(nullable = false)
    private Double quantity;

    @Column(name = "minimum_quantity")
    private Double minimumQuantity;

    @Column(name = "maximum_quantity")
    private Double maximumQuantity;
}
