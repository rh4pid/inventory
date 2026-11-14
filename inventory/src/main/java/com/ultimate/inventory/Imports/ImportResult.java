package com.ultimate.inventory.Imports;

import java.util.ArrayList;
import java.util.List;

/**
 * What an upload did, or why it didn't. Uploads are all or nothing: when {@code errors} is not
 * empty nothing was saved.
 */
public class ImportResult {

    /** A problem in one cell or row. Row is the spreadsheet row number (the header is row 1); 0 = the whole file. */
    public record RowError(int row, String column, String message) {}

    /** Sign-in details for someone whose account the upload created. Shown once. */
    public record Credential(String name, String email, String username, String temporaryPassword) {}

    private static final int MAX_ERRORS = 500;

    private final String kind;
    private final boolean checkOnly;
    private int rows;
    private int created;
    private int updated;
    private int invited;
    private boolean saved;
    private final List<RowError> errors = new ArrayList<>();
    private final List<Credential> credentials = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    public ImportResult(ImportKind kind, boolean checkOnly) {
        this.kind = kind.slug;
        this.checkOnly = checkOnly;
    }

    public void error(int row, String column, String message) {
        if (errors.size() < MAX_ERRORS) errors.add(new RowError(row, column, message));
    }

    public void fileError(String message) {
        error(0, null, message);
    }

    public boolean hasErrors() { return !errors.isEmpty(); }

    public void setRows(int rows) { this.rows = rows; }
    public void addCreated() { created++; }
    public void addUpdated() { updated++; }
    public void addInvited() { invited++; }
    public void addCredential(Credential c) { credentials.add(c); }
    public void note(String note) { if (!notes.contains(note)) notes.add(note); }
    public void markSaved() { saved = true; }

    /** Forget what was counted while saving (used when the save is rolled back). */
    public void discardProgress() {
        created = 0;
        updated = 0;
        invited = 0;
        credentials.clear();
        saved = false;
    }

    public String getKind() { return kind; }
    public boolean isCheckOnly() { return checkOnly; }
    public int getRows() { return rows; }
    public int getCreated() { return created; }
    public int getUpdated() { return updated; }
    public int getInvited() { return invited; }
    public boolean isSaved() { return saved; }
    public boolean isOk() { return errors.isEmpty(); }
    public List<RowError> getErrors() { return errors; }
    public List<Credential> getCredentials() { return credentials; }
    public List<String> getNotes() { return notes; }
}
