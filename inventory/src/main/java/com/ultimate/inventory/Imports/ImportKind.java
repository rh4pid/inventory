package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

/** What a spreadsheet upload creates. The slug is used in URLs and template file names. */
public enum ImportKind {
    PRODUCTS("products", "Products"),
    LOCATIONS("locations", "Locations"),
    PEOPLE("people", "Team members"),
    CUSTOMERS("customers", "Customers"),
    SUPPLIERS("suppliers", "Suppliers"),
    ACCOUNTS("accounts", "Chart of accounts"),
    EMPLOYEES("employees", "Payroll employees");

    public final String slug;
    public final String label;

    ImportKind(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public static ImportKind fromSlug(String slug) {
        return Arrays.stream(values()).filter(k -> k.slug.equalsIgnoreCase(slug)).findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Unknown upload type: " + slug));
    }
}
