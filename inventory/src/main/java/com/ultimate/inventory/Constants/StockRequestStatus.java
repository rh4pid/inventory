package com.ultimate.inventory.Constants;

/** Lifecycle of a stock change waiting for a manager's approval. */
public enum StockRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
