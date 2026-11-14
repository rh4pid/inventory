package com.ultimate.inventory.Constants;

/**
 * Kind of business. Each type carries a sensible default feature preset that is
 * applied when the owner picks it; every flag can still be changed individually.
 */
public enum BusinessType {
    //                        batches expiry  multiUnit approvals
    GENERAL_RETAIL   ("General retail",          false, false, true,  false),
    PROVISION_SHOP   ("Provision / grocery shop", true,  true,  true,  false),
    SUPERMARKET      ("Supermarket",             true,  true,  true,  true),
    PHARMACY         ("Pharmacy / chemist",      true,  true,  true,  true),
    WAREHOUSE        ("Warehouse / distributor", true,  false, true,  true),
    HARDWARE         ("Hardware / building",     false, false, true,  false),
    RESTAURANT       ("Restaurant / bar",        true,  true,  true,  false),
    OTHER            ("Other",                   false, false, false, false);

    private final String label;
    private final boolean trackBatches;
    private final boolean trackExpiry;
    private final boolean multipleUnits;
    private final boolean requireAdjustmentApproval;

    BusinessType(String label, boolean trackBatches, boolean trackExpiry,
                 boolean multipleUnits, boolean requireAdjustmentApproval) {
        this.label = label;
        this.trackBatches = trackBatches;
        this.trackExpiry = trackExpiry;
        this.multipleUnits = multipleUnits;
        this.requireAdjustmentApproval = requireAdjustmentApproval;
    }

    public String getLabel() { return label; }
    public boolean isTrackBatches() { return trackBatches; }
    public boolean isTrackExpiry() { return trackExpiry; }
    public boolean isMultipleUnits() { return multipleUnits; }
    public boolean isRequireAdjustmentApproval() { return requireAdjustmentApproval; }
}
