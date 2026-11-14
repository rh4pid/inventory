package com.ultimate.inventory.Imports;

import java.util.ArrayList;
import java.util.List;

/**
 * One kind of spreadsheet upload. Works in two passes so a file is saved all or nothing:
 * {@link #prepare} checks every row without saving anything and returns the steps to save them;
 * the steps then run in one transaction (see ImportService).
 */
public interface Importer {

    ImportKind kind();

    List<ImportColumn> columns();

    /** Extra lines for the template's instructions sheet. */
    default List<String> notes() { return List.of(); }

    /** Fails if this person may not upload this kind for this company. */
    void authorize(Long businessId, String email);

    /** Checks every row (problems go to {@code result}) and returns what to save. Must not save anything. */
    Plan prepare(Long businessId, List<SheetRow> rows, ImportResult result, String email);

    /** Saves one row (or does a final step). Throwing stops the upload and undoes everything. */
    @FunctionalInterface
    interface Action {
        void run(ImportResult result);
    }

    /** A step and the spreadsheet row it came from (0 = the whole file). */
    record Step(int row, Action action) {}

    final class Plan {
        private final List<Step> steps = new ArrayList<>();

        public Plan add(int row, Action action) {
            steps.add(new Step(row, action));
            return this;
        }

        public List<Step> steps() { return steps; }
    }
}
