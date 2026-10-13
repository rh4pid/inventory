package com.ultimate.inventory.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "invoice_detail")
public class InvoiceDetail extends Model {

    @ManyToOne
    private Invoice invoice;

    @ManyToOne
    private Product product;

    private Integer quantity;
    @Column(name = "unit_price")
    private BigDecimal unitPrice;
    @Column(name = "total_line")
    private BigDecimal totalLine;


}
