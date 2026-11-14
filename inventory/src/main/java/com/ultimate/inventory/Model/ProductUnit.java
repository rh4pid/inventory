package com.ultimate.inventory.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * An alternative pack size a product can be bought or sold in.
 * Example: base unit = Piece, ProductUnit(Carton, factor 24) → 1 Carton = 24 Pieces.
 * Stock is always held in the product's base unit; these only convert on entry.
 */
@Getter
@Setter
@Entity
@Table(name = "product_unit",
       uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "unit_id"}))
public class ProductUnit extends Model {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    /** How many base units are in one of this unit. */
    @Column(name = "conversion_factor", nullable = false)
    private Double conversionFactor;

    /** Optional barcode printed on this pack (e.g. the carton barcode). */
    @Column
    private String barcode;

    /** Optional fixed selling price for the whole pack; null = base price × factor. */
    @Column(name = "selling_price")
    private Double sellingPrice;
}
