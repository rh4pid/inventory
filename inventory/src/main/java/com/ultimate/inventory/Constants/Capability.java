package com.ultimate.inventory.Constants;

import java.util.*;

/**
 * What a role lets someone do inside a company. This is the single place that decides it: the
 * backend checks these on every request, and the app shows menus and buttons from the same list
 * (sent with each membership).
 *
 *                     Owner/Admin  Manager  Cashier  Staff  Driver
 *   settings, accounts, payroll   ✓         -        -       -      -
 *   team, locations, catalogue    ✓         ✓        -       -      -
 *   sell, customers               ✓         ✓        ✓       -      -
 *   purchases (receive)           ✓         ✓        -       ✓      -
 *   stock in, counts              ✓         ✓        -       ✓      -
 *   transfers                     ✓         ✓        -       ✓      ✓
 *   stock out / adjust            ✓         ✓      request request  -
 *   approvals, returns, voids     ✓         ✓        -       -      -
 *   record expenses               ✓         ✓     request      -      -
 *   cost prices & stock value     ✓         ✓        -       -      -
 */
public enum Capability {
    /** Business type, features, business details. */
    MANAGE_SETTINGS,
    /** Chart of accounts. */
    MANAGE_ACCOUNTS,
    /** Employees' HR records and salaries. */
    MANAGE_PAYROLL,
    /** People's sign-ins, roles and locations (managers: front-line staff only). */
    MANAGE_TEAM,
    /** Add, edit and remove locations. */
    MANAGE_LOCATIONS,
    /** Products, prices, categories, units and pack sizes. */
    MANAGE_CATALOG,
    /** Add, edit and remove suppliers. */
    MANAGE_SUPPLIERS,
    /** Delete customers. */
    DELETE_CUSTOMERS,

    /** See products and stock levels (always at the person's own locations). */
    VIEW_STOCK,
    /** See cost prices, stock value and margins. */
    VIEW_COSTS,

    /** Make sales (POS / new sale), see sales, take customer payments. */
    SELL,
    /** Add and edit customers. */
    MANAGE_CUSTOMERS,
    /** Record purchases / deliveries and see them; pick suppliers. */
    RECEIVE,
    /** Record payments to suppliers. */
    PAY_SUPPLIERS,

    STOCK_IN,
    /** Stock out (for front-line roles this becomes a request when approvals are on). */
    STOCK_OUT,
    /** Adjust / correct stock (a request for front-line roles when approvals are on). */
    ADJUST,
    TRANSFER,
    COUNT,

    /** Approve or reject stock requests and expenses, post counts. */
    APPROVE,
    /** Record business expenses (cashiers' expenses wait for a manager's approval). */
    RECORD_EXPENSES,

    /** Returns, refunds and voids. */
    RETURNS_AND_VOIDS;

    private static final Map<UserRoles, Set<Capability>> BY_ROLE = new EnumMap<>(UserRoles.class);

    static {
        Set<Capability> all = EnumSet.allOf(Capability.class);
        Set<Capability> manager = EnumSet.complementOf(EnumSet.of(MANAGE_SETTINGS, MANAGE_ACCOUNTS, MANAGE_PAYROLL));
        Set<Capability> cashier = EnumSet.of(VIEW_STOCK, SELL, MANAGE_CUSTOMERS, STOCK_OUT, ADJUST, RECORD_EXPENSES);
        Set<Capability> staff = EnumSet.of(VIEW_STOCK, RECEIVE, STOCK_IN, STOCK_OUT, ADJUST, TRANSFER, COUNT);
        Set<Capability> driver = EnumSet.of(VIEW_STOCK, TRANSFER);

        BY_ROLE.put(UserRoles.OWNER, all);
        BY_ROLE.put(UserRoles.ADMIN, all);
        BY_ROLE.put(UserRoles.MANAGER, manager);
        BY_ROLE.put(UserRoles.CASHIER, cashier);
        BY_ROLE.put(UserRoles.SHOPKEEP, cashier); // legacy name for a cashier
        BY_ROLE.put(UserRoles.STAFF, staff);
        BY_ROLE.put(UserRoles.DRIVER, driver);
    }

    public static Set<Capability> of(UserRoles role) {
        return role == null ? Set.of() : Collections.unmodifiableSet(BY_ROLE.getOrDefault(role, Set.of()));
    }

    public static boolean has(UserRoles role, Capability c) {
        return of(role).contains(c);
    }
}
