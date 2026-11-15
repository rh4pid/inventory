package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A product's picture, kept in the database (the server's disk is wiped on every deploy).
 * The browser shrinks pictures before upload, so each is small (tens of KB).
 * Served publicly at /public/product-images/{imageKey}: the key is random and changes on every upload.
 */
@Getter
@Setter
@Entity
@Table(name = "product_image")
public class ProductImage extends Model {

    @OneToOne(optional = false)
    @JoinColumn(name = "product", unique = true)
    private Product product;

    @Column(name = "image_key", nullable = false, unique = true, length = 40)
    private String imageKey;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "data", nullable = false, length = 2_000_000)
    @Basic(fetch = FetchType.LAZY)
    private byte[] data;
}
