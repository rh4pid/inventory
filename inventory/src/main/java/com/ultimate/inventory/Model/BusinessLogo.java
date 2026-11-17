package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A company's logo, kept in the database (the server's disk is wiped on every deploy).
 * Served publicly at /public/business-logos/{imageKey}: the key is random and changes on every upload.
 * Business.businessLogo holds the current key.
 */
@Getter
@Setter
@Entity
@Table(name = "business_logo_image")
public class BusinessLogo extends Model {

    @OneToOne(optional = false)
    @JoinColumn(name = "business", unique = true)
    private Business business;

    @Column(name = "image_key", nullable = false, unique = true, length = 40)
    private String imageKey;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "data", nullable = false, length = 2_000_000)
    @Basic(fetch = FetchType.LAZY)
    private byte[] data;
}
