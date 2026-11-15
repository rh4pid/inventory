package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

/** What a spreadsheet upload creates. The slug is used in URLs and template file names. */
public enum ImportKind {
    PRODUCTS("products", "Products", "Your catalogue with prices, and optional opening stock per location."),
    CATEGORIES("categories", "Categories", "Product categories, with sub-categories inside them."),
    PRODUCT_FEATURES("product-features", "Product features", "Features such as Color and Size, and each product's value for them."),
    SUPPLIERS("suppliers", "Suppliers", "The companies you buy from and who you deal with there."),
    EMPLOYEES("employees", "Payroll employees", "Position, department and salary for people on your team."),
    CUSTOMERS("customers", "Customers", "Customer names and contact details."),
    LOCATIONS("locations", "Locations", "Shops, warehouses, storerooms and vans."),
    PEOPLE("people", "Team members", "New people get a sign-in; people with an account get an invitation."),
    ACCOUNTS("accounts", "Chart of accounts", "Extra accounts and opening balances for your books.");

    public final String slug;
    public final String label;
    public final String description;

    ImportKind(String slug, String label, String description) {
        this.slug = slug;
        this.label = label;
        this.description = description;
    }

    public static ImportKind fromSlug(String slug) {
        return Arrays.stream(values()).filter(k -> k.slug.equalsIgnoreCase(slug)).findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Unknown upload type: " + slug));
    }
}
