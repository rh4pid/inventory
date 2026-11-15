package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Spreadsheet uploads, all or nothing: every row is checked first; if any row has a problem nothing
 * is saved and every problem is listed. Otherwise all rows are saved in one transaction, so a
 * failure part-way through undoes the rows already saved.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final Map<ImportKind, Importer> importers = new EnumMap<>(ImportKind.class);

    public ImportService(List<Importer> all) {
        all.forEach(i -> importers.put(i.kind(), i));
    }

    private Importer importer(ImportKind kind) {
        Importer i = importers.get(kind);
        if (i == null) throw new AppException(HttpStatus.NOT_FOUND, "Uploads of " + kind.label + " aren't available");
        return i;
    }

    /** One upload type, for the templates page: what it's for and what its template looks like. */
    public record TemplateInfo(String kind, String label, String description, List<ImportColumn> columns, List<String> notes) {}

    /** The upload types this person may use in this company (in the order of ImportKind). */
    public List<TemplateInfo> catalog(Long businessId, String email) {
        List<TemplateInfo> list = new java.util.ArrayList<>();
        for (ImportKind kind : ImportKind.values()) {
            Importer imp = importers.get(kind);
            if (imp == null) continue;
            try {
                imp.authorize(businessId, email);
            } catch (AppException notAllowed) {
                continue;
            }
            list.add(new TemplateInfo(kind.slug, kind.label, kind.description, imp.columns(), imp.notes()));
        }
        return list;
    }

    public byte[] template(Long businessId, ImportKind kind, String email) {
        Importer imp = importer(kind);
        imp.authorize(businessId, email);
        return TemplateWriter.write(kind, imp.columns(), imp.notes());
    }

    /**
     * Checks the file, and saves it unless {@code checkOnly} or a problem is found.
     * Problems come back in the result (not as an HTTP error) so they can be listed row by row.
     */
    @Transactional
    public ImportResult run(Long businessId, ImportKind kind, MultipartFile file, boolean checkOnly, String email) {
        Importer imp = importer(kind);
        imp.authorize(businessId, email);
        ImportResult result = new ImportResult(kind, checkOnly);

        List<SheetRow> rows = SheetReader.read(file, imp.columns(), result);
        result.setRows(rows.size());
        if (result.hasErrors()) return result;

        Importer.Plan plan = imp.prepare(businessId, rows, result, email);
        if (result.hasErrors() || checkOnly) return result;

        for (Importer.Step step : plan.steps()) {
            try {
                step.action().run(result);
            } catch (RuntimeException e) {
                // Undo everything saved so far; the caller gets the row that failed.
                rollback();
                result.discardProgress();
                String message = e instanceof AppException ? e.getMessage() : "Couldn't be saved (" + e.getClass().getSimpleName() + ")";
                if (step.row() > 0) result.error(step.row(), null, message + ". Nothing was saved.");
                else result.fileError(message + ". Nothing was saved.");
                if (!(e instanceof AppException)) log.error("{} upload failed at row {} for business {}", kind, step.row(), businessId, e);
                return result;
            }
        }
        result.markSaved();
        log.info("{} uploaded {} rows of {} to business {} ({} created, {} updated, {} invited)",
                email, rows.size(), kind, businessId, result.getCreated(), result.getUpdated(), result.getInvited());
        return result;
    }

    private static void rollback() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (org.springframework.transaction.NoTransactionException e) {
            // Only outside Spring (unit tests): there is nothing to undo.
        }
    }
}
