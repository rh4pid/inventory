package com.ultimate.inventory.DTO;

import com.ultimate.inventory.Constants.BusinessType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** Partial update: any field left null is not changed. */
@Getter
@Setter
public class BusinessSettingsDTO {
    private BusinessType businessType;
    /** When true together with businessType, reset the feature switches to that type's defaults. */
    private Boolean applyPreset;
    private String currencyCode;
    private String currencySymbol;
    private Boolean taxEnabled;
    private String vatNumber;
    private Boolean lowStockAlerts;
    private Boolean trackBatches;
    private Boolean trackExpiry;
    @Min(1) @Max(365)
    private Integer expiryAlertDays;
    private Boolean multipleUnits;
    private Boolean allowNegativeStock;
    private Boolean requireAdjustmentApproval;
}
