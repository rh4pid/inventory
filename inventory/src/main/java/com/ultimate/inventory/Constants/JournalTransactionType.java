package com.ultimate.inventory.Constants;

public enum JournalTransactionType {
    SALE_CASH,
    SALE_CREDIT,
    PURCHASE_CASH,
    PURCHASE_CREDIT,
    CUSTOMER_PAYMENT,
    SUPPLIER_PAYMENT,
    SALE_RETURN,
    PURCHASE_RETURN,
    /** A business expense (electricity, water…), usually paid from petty cash. */
    EXPENSE_CASH,
    EXPENSE_CREDIT,
    STOCK_ADJUSTMENT,
    STOCK_TRANSFER,
    OPENING_BALANCE,
    /** Owner puts money into the business. */
    OWNER_CAPITAL,
    /** Owner takes money out for personal use. */
    OWNER_DRAWINGS,
    /** Money moved between the company's own accounts (e.g. topping up petty cash, banking the takings). */
    FUND_TRANSFER,
    /** An entry typed in by an accountant. */
    MANUAL,
    OTHER
}
