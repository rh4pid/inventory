package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product")
public class Product extends Model {

    @ManyToOne
    @JoinColumn(name = "business")
    private Business business;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @ManyToOne
    @JoinColumn(name = "tax_rate_id")
    private TaxRate taxRate;

    @Column(name = "product_name")
    private String productName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String sku;

    @Column
    private String barcode;

    @Column
    private String image;

    // Only owners, admins and managers see costs (see CostVisibility).

    @tools.jackson.databind.annotation.JsonSerialize(using = com.ultimate.inventory.utils.CostVisibility.Serializer.class)

    @Column(name = "cost_price")
    private Double costPrice;

    @Column(name = "selling_price")
    private Double sellingPrice;

    @Column(name = "discount_type")
    private String discountType;

    @Column(name = "discount_value")
    private Double discountValue;

    @Column(name = "actual_price")
    private Double actualPrice;

    @Column(name = "total_count")
    private Double totalCount = 0.0;

    @Column(name = "reorder_level")
    private Integer reorderLevel;

    private Boolean deleted = false;
}
