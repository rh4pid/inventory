package com.ultimate.inventory.Imports;

import java.util.List;

/**
 * One column of an upload template.
 *
 * @param key      internal name, also accepted as a header
 * @param header   the header people see in the template
 * @param required whether every row must fill it in
 * @param help     what to put in it (shown in the template's instructions sheet)
 * @param example  an example value
 * @param options  allowed values, offered as a dropdown in the template (empty for free text)
 */
public record ImportColumn(String key, String header, boolean required, String help, String example, List<String> options) {

    public static ImportColumn required(String key, String header, String help, String example) {
        return new ImportColumn(key, header, true, help, example, List.of());
    }

    public static ImportColumn optional(String key, String header, String help, String example) {
        return new ImportColumn(key, header, false, help, example, List.of());
    }

    public ImportColumn withOptions(List<String> values) {
        return new ImportColumn(key, header, required, help, example, values);
    }

    /** The header as it appears in the template (required columns end with *). */
    public String title() {
        return required ? header + " *" : header;
    }
}
