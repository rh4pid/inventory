package com.ultimate.inventory.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A product's value for one feature, e.g. (Polo shirt, Color, "Red"). One value per feature per product. */
@Getter
@Setter
@Entity
@Table(name = "product_feature",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_feature", columnNames = {"product_id", "feature_id"}))
public class ProductFeature extends Model {

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(optional = false)
    @JoinColumn(name = "feature_id")
    private Feature feature;

    @Column(name = "value", nullable = false, length = 120)
    private String value;
}
