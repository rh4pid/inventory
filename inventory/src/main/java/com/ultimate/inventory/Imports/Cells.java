package com.ultimate.inventory.Imports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

/**
 * Reads typed values from a row, reporting problems against the right row and column.
 * Each method returns null (and records an error when the value is required or malformed).
 */
public final class Cells {

    private final SheetRow row;
    private final ImportResult result;
    private final Map<String, ImportColumn> byKey;
    private boolean ok = true;

    public Cells(SheetRow row, ImportResult result, List<ImportColumn> columns) {
        this.row = row;
        this.result = result;
        this.byKey = new HashMap<>();
        columns.forEach(c -> byKey.put(c.key(), c));
    }

    /** False once any problem was found in this row. */
    public boolean ok() { return ok; }

    public int number() { return row.number(); }

    public void error(String key, String message) {
        ok = false;
        result.error(row.number(), header(key), message);
    }

    private String header(String key) {
        ImportColumn c = byKey.get(key);
        return c == null ? key : c.header();
    }

    private boolean required(String key) {
        ImportColumn c = byKey.get(key);
        return c != null && c.required();
    }

    public String text(String key) {
        String v = row.get(key);
        if (v == null && required(key)) error(key, "Required");
        return v;
    }

    public String text(String key, int maxLength) {
        String v = text(key);
        if (v != null && v.length() > maxLength) {
            error(key, "Too long (at most " + maxLength + " characters)");
            return null;
        }
        return v;
    }

    public BigDecimal decimal(String key) {
        String v = text(key);
        if (v == null) return null;
        try {
            return new BigDecimal(v.replace(",", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            error(key, "\"" + v + "\" isn't a number");
            return null;
        }
    }

    /** A number greater than zero. */
    public BigDecimal positive(String key) {
        BigDecimal d = decimal(key);
        if (d != null && d.signum() <= 0) {
            error(key, "Must be more than 0");
            return null;
        }
        return d;
    }

    /** A number of zero or more. */
    public BigDecimal notNegative(String key) {
        BigDecimal d = decimal(key);
        if (d != null && d.signum() < 0) {
            error(key, "Can't be negative");
            return null;
        }
        return d;
    }

    public Integer wholeNumber(String key) {
        BigDecimal d = notNegative(key);
        if (d == null) return null;
        try {
            return d.intValueExact();
        } catch (ArithmeticException e) {
            error(key, "Must be a whole number");
            return null;
        }
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"));

    /** yyyy-mm-dd (or dd/mm/yyyy). */
    public LocalDate date(String key) {
        String v = text(key);
        if (v == null) return null;
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        error(key, "\"" + v + "\" isn't a date. Use yyyy-mm-dd, e.g. 2026-01-31");
        return null;
    }

    /**
     * One of a fixed set of values, matched without caring about case, spaces or underscores
     * ("cash on hand" matches CASH_ON_HAND). {@code labels} maps each value to the words people use.
     */
    public <E extends Enum<E>> E choice(String key, Class<E> type, Function<E, String> label) {
        String v = text(key);
        if (v == null) return null;
        String want = squash(v);
        for (E e : type.getEnumConstants()) {
            if (squash(e.name()).equals(want) || squash(label.apply(e)).equals(want)) return e;
        }
        List<String> allowed = new ArrayList<>();
        for (E e : type.getEnumConstants()) allowed.add(label.apply(e));
        error(key, "\"" + v + "\" isn't one of: " + String.join(", ", allowed));
        return null;
    }

    /** A comma- or semicolon-separated list (e.g. several locations in one cell). */
    public List<String> list(String key) {
        String v = text(key);
        if (v == null) return List.of();
        return Arrays.stream(v.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static String squash(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** "WAREHOUSE" -> "Warehouse", "MOBILE_MONEY" -> "Mobile money". */
    public static String pretty(Enum<?> e) {
        String s = e.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
