package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Imports.ImportKind;
import com.ultimate.inventory.Imports.ImportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Spreadsheet uploads: download a template, check a filled-in file, then save it (all or nothing). */
@RestController
@RequestMapping("/businesses/{businessId}/imports")
public class ImportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /** Every upload type you may use, with its columns, for the templates page. */
    @GetMapping
    public ResponseEntity<?> catalog(@PathVariable Long businessId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(importService.catalog(businessId, email));
    }

    @GetMapping("/{kind}/template")
    public ResponseEntity<byte[]> template(@PathVariable Long businessId, @PathVariable String kind,
                                           @AuthenticationPrincipal String email) {
        ImportKind k = ImportKind.fromSlug(kind);
        byte[] file = importService.template(businessId, k, email);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(k.slug + "-template.xlsx").build().toString())
                .body(file);
    }

    /** {@code check=true} only checks the file; otherwise it's saved if every row is fine. */
    @PostMapping(value = "/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@PathVariable Long businessId, @PathVariable String kind,
                                    @RequestPart("file") MultipartFile file,
                                    @RequestParam(defaultValue = "false") boolean check,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(importService.run(businessId, ImportKind.fromSlug(kind), file, check, email));
    }
}
