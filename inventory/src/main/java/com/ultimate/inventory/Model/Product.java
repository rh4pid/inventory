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
    @Column(name = "product_name")
    private String productName;
    @Column(name = "cost_price")
    private Double costPrice;
    @Column(name = "selling_price")
    private Double sellingPrice;
    @Column(name = "discount_type")
    private String discountType; //flat or percentage
    @Column(name = "discount_value")
    private Double discountValue;
    @Column(name = "actual_price")
    private Double actualPrice;
    @Column(name = "total_count")
    private Double totalCount;
    @Column(name = "reorder_level")
    private Integer reorderLevel;
}
