package com.ultimate.inventory.Imports;

import java.util.Map;

/**
 * One filled-in row of an upload: its spreadsheet row number and its cells by column key
 * (blank cells are absent).
 */
public record SheetRow(int number, Map<String, String> cells) {

    /** The cell's text, trimmed, or null when blank. */
    public String get(String key) {
        String v = cells.get(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    public boolean has(String key) {
        return get(key) != null;
    }
}
