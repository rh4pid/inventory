package com.ultimate.inventory.Model;

import com.ultimate.inventory.Constants.InvoiceType;
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
@Table(name = "invoice")
public class Invoice extends Model {

    @ManyToOne
    private Supplier supplier;

    private InvoiceType type;
    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;
    private String status;


}
