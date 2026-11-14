package com.ultimate.inventory.Constants;

public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    /** Withdrawn by the company before the person answered. */
    CANCELLED,
    /** Not answered in time. */
    EXPIRED
}
