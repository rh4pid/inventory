package com.ultimate.inventory.Imports;

import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Reads the first sheet of an .xlsx/.xls upload. Columns are matched by header, so their order
 * doesn't matter and extra columns are ignored. Blank rows are skipped.
 */
public final class SheetReader {

    public static final int MAX_ROWS = 5000;
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private SheetReader() {}

    /** The filled-in rows, or an empty list with file-level errors added to {@code result}. */
    public static List<SheetRow> read(MultipartFile file, List<ImportColumn> columns, ImportResult result) {
        if (file == null || file.isEmpty()) {
            result.fileError("Choose a spreadsheet to upload");
            return List.of();
        }
        if (file.getSize() > MAX_BYTES) {
            result.fileError("The file is larger than 10 MB. Split it into smaller files.");
            return List.of();
        }
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() == 0) {
                result.fileError("The file has no sheets");
                return List.of();
            }
            Sheet sheet = wb.getSheetAt(0);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            return read(sheet, evaluator, columns, result);
        } catch (Exception e) {
            result.fileError("This file can't be read. Upload an Excel file (.xlsx), ideally made from the template.");
            return List.of();
        }
    }

    private static List<SheetRow> read(Sheet sheet, FormulaEvaluator evaluator, List<ImportColumn> columns, ImportResult result) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            result.fileError("The first row must hold the column headers. Download the template to see them.");
            return List.of();
        }
        // Which spreadsheet column holds which of our columns.
        Map<String, Integer> where = new HashMap<>();
        for (Cell cell : header) {
            String h = normalize(text(cell, evaluator));
            if (h.isEmpty()) continue;
            for (ImportColumn c : columns) {
                if (!where.containsKey(c.key()) && (h.equals(normalize(c.header())) || h.equals(normalize(c.key())))) {
                    where.put(c.key(), cell.getColumnIndex());
                }
            }
        }
        boolean missing = false;
        for (ImportColumn c : columns) {
            if (c.required() && !where.containsKey(c.key())) {
                result.fileError("The column \"" + c.header() + "\" is missing. Download the template to see the columns.");
                missing = true;
            }
        }
        if (missing) return List.of();

        List<SheetRow> rows = new ArrayList<>();
        for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, String> cells = new HashMap<>();
            for (Map.Entry<String, Integer> e : where.entrySet()) {
                String v = text(row.getCell(e.getValue()), evaluator);
                if (!v.isBlank()) cells.put(e.getKey(), v.trim());
            }
            if (cells.isEmpty()) continue;
            if (rows.size() >= MAX_ROWS) {
                result.fileError("The file has more than " + MAX_ROWS + " rows. Split it into smaller files.");
                return List.of();
            }
            rows.add(new SheetRow(r + 1, cells)); // people count rows from 1
        }
        if (rows.isEmpty()) result.fileError("The file has headers but no rows to upload");
        return rows;
    }

    /** "Cost price *" -> "costprice". */
    static String normalize(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT).replaceAll("\\(.*?\\)", "").replaceAll("[^a-z0-9]", "");
    }

    /** The cell as text: numbers without formatting (1200.5, not "1,200.50"), dates as yyyy-mm-dd. */
    private static String text(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            try {
                CellValue v = evaluator.evaluate(cell);
                if (v == null) return "";
                return switch (v.getCellType()) {
                    case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                            ? new SimpleDateFormat("yyyy-MM-dd").format(DateUtil.getJavaDate(v.getNumberValue()))
                            : plain(v.getNumberValue());
                    case STRING -> v.getStringValue();
                    case BOOLEAN -> String.valueOf(v.getBooleanValue());
                    default -> "";
                };
            } catch (Exception e) {
                return "";
            }
        }
        return switch (type) {
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? new SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue())
                    : plain(cell.getNumericCellValue());
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private static String plain(double d) {
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }
}
