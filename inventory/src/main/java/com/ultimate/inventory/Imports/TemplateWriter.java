package com.ultimate.inventory.Imports;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Builds the .xlsx template for an upload: a sheet to fill in (headers, dropdowns for fixed values)
 * and an Instructions sheet explaining every column with an example.
 */
public final class TemplateWriter {

    private static final int DROPDOWN_ROWS = SheetReader.MAX_ROWS + 1;

    private TemplateWriter() {}

    public static byte[] write(ImportKind kind, List<ImportColumn> columns, List<String> extraNotes) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@")); // keep codes like 0244… as typed

            // The sheet to fill in.
            Sheet data = wb.createSheet(kind.label);
            Row h = data.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                ImportColumn c = columns.get(i);
                Cell cell = h.createCell(i);
                cell.setCellValue(c.title());
                cell.setCellStyle(head);
                data.setColumnWidth(i, Math.min(60, Math.max(14, c.title().length() + 4)) * 256);
                data.setDefaultColumnStyle(i, text);
                if (!c.options().isEmpty()) {
                    DataValidationHelper dv = data.getDataValidationHelper();
                    DataValidation v = dv.createValidation(
                            dv.createExplicitListConstraint(c.options().toArray(String[]::new)),
                            new CellRangeAddressList(1, DROPDOWN_ROWS, i, i));
                    v.setShowErrorBox(true);
                    v.createErrorBox("Choose from the list", "Pick one of: " + String.join(", ", c.options()));
                    data.addValidationData(v);
                }
            }
            data.createFreezePane(0, 1);

            // How to fill it in.
            Sheet help = wb.createSheet("Instructions");
            int r = 0;
            Row title = help.createRow(r++);
            title.createCell(0).setCellValue(kind.label + ": how to fill in this template");
            title.getCell(0).setCellStyle(head);
            help.createRow(r++).createCell(0).setCellValue(
                    "Fill in the first sheet, one row per item. Columns marked * are required. Keep the header row as it is.");
            help.createRow(r++).createCell(0).setCellValue(
                    "The whole file is checked first: if any row has a problem nothing is saved, and you get a list of what to fix.");
            for (String note : extraNotes) help.createRow(r++).createCell(0).setCellValue(note);
            r++;
            Row cols = help.createRow(r++);
            String[] titles = {"Column", "Required", "What to enter", "Example", "Allowed values"};
            for (int i = 0; i < titles.length; i++) {
                cols.createCell(i).setCellValue(titles[i]);
                cols.getCell(i).setCellStyle(head);
            }
            for (ImportColumn c : columns) {
                Row row = help.createRow(r++);
                row.createCell(0).setCellValue(c.header());
                row.createCell(1).setCellValue(c.required() ? "Yes" : "No");
                row.createCell(2).setCellValue(c.help());
                row.createCell(3).setCellValue(c.example() == null ? "" : c.example());
                row.createCell(4).setCellValue(String.join(", ", c.options()));
            }
            help.setColumnWidth(0, 26 * 256);
            help.setColumnWidth(1, 10 * 256);
            help.setColumnWidth(2, 80 * 256);
            help.setColumnWidth(3, 24 * 256);
            help.setColumnWidth(4, 40 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
