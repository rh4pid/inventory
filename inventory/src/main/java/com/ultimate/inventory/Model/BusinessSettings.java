package com.ultimate.inventory.Model;

import com.ultimate.inventory.Constants.BusinessType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "business_settings")
public class BusinessSettings extends Model {

    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "business_id", nullable = false, unique = true)
    private Business business;

    @Column(name = "currency_code", length = 10)
    private String currencyCode = "USD";

    @Column(name = "currency_symbol", length = 10)
    private String currencySymbol = "$";

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", length = 50)
    private BusinessType businessType = BusinessType.GENERAL_RETAIL;

    @Column(name = "tax_enabled")
    private Boolean taxEnabled = false;

    @Column(name = "vat_number")
    private String vatNumber;

    @Column(name = "low_stock_alerts")
    private Boolean lowStockAlerts = true;

    // ── Stock features (switch on per business) ──────────────────────────────

    /** Hold stock in batches / lots (receive date, cost, lot number). */
    @Column(name = "track_batches")
    private Boolean trackBatches = false;

    /** Record expiry dates on batches and sell earliest-expiring first. */
    @Column(name = "track_expiry")
    private Boolean trackExpiry = false;

    /** Days before expiry at which a batch is flagged as expiring soon. */
    @Column(name = "expiry_alert_days")
    private Integer expiryAlertDays = 30;

    /** Products can be bought / sold in pack sizes (carton, box, dozen…). */
    @Column(name = "multiple_units")
    private Boolean multipleUnits = true;

    /** Stock may go below zero (e.g. sell before a delivery is recorded). */
    @Column(name = "allow_negative_stock")
    private Boolean allowNegativeStock = false;

    /** Colour theme id chosen by the company admin (null = automatic, from the business type). */
    @Column(name = "theme", length = 32)
    private String theme;

    /** Adjustments / write-offs by non-managers need manager approval. */
    @Column(name = "require_adjustment_approval")
    private Boolean requireAdjustmentApproval = false;

    public void applyPreset(BusinessType type) {
        this.businessType = type;
        this.trackBatches = type.isTrackBatches();
        this.trackExpiry = type.isTrackExpiry();
        this.multipleUnits = type.isMultipleUnits();
        this.requireAdjustmentApproval = type.isRequireAdjustmentApproval();
    }
}
