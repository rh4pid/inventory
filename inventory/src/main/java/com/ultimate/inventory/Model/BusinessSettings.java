package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "business_settings")
public class BusinessSettings extends Model {

    @OneToOne
    @JoinColumn(name = "business_id", nullable = false, unique = true)
    private Business business;

    @Column(name = "currency_code", length = 10)
    private String currencyCode = "USD";

    @Column(name = "currency_symbol", length = 10)
    private String currencySymbol = "$";

    @Column(name = "business_type", length = 50)
    private String businessType;

    @Column(name = "tax_enabled")
    private Boolean taxEnabled = false;

    @Column(name = "vat_number")
    private String vatNumber;

    @Column(name = "low_stock_alerts")
    private Boolean lowStockAlerts = true;
}
