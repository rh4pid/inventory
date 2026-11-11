package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tax_rate")
public class TaxRate extends Model {

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double rate;

    @Column(nullable = false)
    private Boolean active = true;
}
