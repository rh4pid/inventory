package com.ultimate.inventory.Model;

import com.ultimate.inventory.Constants.LocationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "shop")
public class Shop extends Model {

    @ManyToOne
    @JoinColumn(name = "business")
    private Business business;

    @Column(name = "shop_code", unique = true, nullable = false)
    private String shopCode;

    @Column(name = "shop_name")
    private String shopName;

    /** What kind of stock location this is (shop, warehouse, storeroom…). */
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", length = 20)
    private LocationType locationType = LocationType.SHOP;

    @Column(name = "shop_location")
    private String shopLocation;

    @Column(name = "total_products")
    private Integer totalProducts = 0;

    @Column(name = "total_cost_price")
    private Double totalCostPrice = 0.0;

    @Column(name = "total_selling_price")
    private Double totalSellingPrice = 0.0;

    @Column(name = "total_sales")
    private Double totalSales = 0.0;

    private Boolean active; //business control level;
    private Boolean locked;
    private Boolean suspended;
    private Boolean deleted;
}
