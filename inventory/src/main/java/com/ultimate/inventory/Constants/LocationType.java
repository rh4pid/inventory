package com.ultimate.inventory.Constants;

/** What kind of place holds stock. A business can mix these freely. */
public enum LocationType {
    SHOP,       // sells to customers (counter / POS)
    WAREHOUSE,  // bulk storage, usually no direct sales
    STOREROOM,  // back-of-house storage attached to a shop
    PHARMACY,   // dispensary
    VEHICLE,    // van / delivery truck stock
    OTHER
}
