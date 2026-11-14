package com.ultimate.inventory.Constants;

/**
 * What the system uses an account for. Postings find their accounts by purpose, so each purpose
 * except EXPENSE and OTHER has exactly one account per company (EXPENSE covers electricity, water,
 * rent… — many accounts, picked by the person recording the expense).
 */
public enum AccountPurpose {
    CASH,
    BANK,
    /** Mobile money wallet (MTN MoMo, Telecel Cash, AirtelTigo Money…). */
    MOBILE_MONEY,
    /** The company's petty cash box, used for small branch expenses. */
    PETTY_CASH,
    ACCOUNTS_RECEIVABLE,
    INVENTORY,
    ACCOUNTS_PAYABLE,
    /** Money the owner put into the business. */
    CAPITAL,
    /** Money the owner took out of the business for personal use. */
    DRAWINGS,
    /** Profits kept in the business from earlier periods. */
    RETAINED_EARNINGS,
    SALES_REVENUE,
    OTHER_INCOME,
    COST_OF_GOODS_SOLD,
    PURCHASE,
    DISCOUNT_RECEIVED,
    DISCOUNT_ALLOWED,
    /** Stock written off (damage, theft, expiry) or found on a count. */
    STOCK_ADJUSTMENT,
    TAX_PAYABLE,
    TAX_RECEIVABLE,
    EXPENSE,
    OTHER;

    /** Accounts that hold money: what expenses are paid from and transfers move between. */
    public boolean isMoney() {
        return this == CASH || this == BANK || this == MOBILE_MONEY || this == PETTY_CASH;
    }

    /** Purposes the system posts to automatically; one account each, which can't be switched off. */
    public boolean isSystem() {
        return this != EXPENSE && this != OTHER;
    }
}
