package com.ultimate.inventory.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "supplier_product")
public class SupplierProduct extends Model {

    @ManyToOne
    @JoinColumn(name = "supplier")
    private Supplier supplier;

    @ManyToOne
    @JoinColumn(name = "product")
    private Product product;
}
