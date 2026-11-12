package com.ultimate.inventory.utils;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Whether the current request may see cost prices and stock value. Set when the caller's company
 * membership is checked (BusinessAccessHelper), cleared at the end of the request
 * (CostVisibilityFilter). Hidden unless explicitly allowed, so a forgotten check fails closed.
 */
public final class CostVisibility {

    private static final ThreadLocal<Boolean> VISIBLE = new ThreadLocal<>();

    private CostVisibility() {}

    public static void set(boolean visible) { VISIBLE.set(visible); }

    public static boolean visible() { return Boolean.TRUE.equals(VISIBLE.get()); }

    public static void clear() { VISIBLE.remove(); }

    /** Writes the value only for people allowed to see costs; null for everyone else. */
    public static class Serializer extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
            if (visible() && value != null) ctxt.writeValue(gen, value);
            else gen.writeNull();
        }
    }
}
