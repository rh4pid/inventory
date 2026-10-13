package com.ultimate.inventory.utils;

import com.ultimate.inventory.DTO.ResponseDTO;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelService {

    public <T> List<T> readExcel(MultipartFile file, Class<T> excelDTO) {
        List<T> result = new ArrayList<>();
        ResponseDTO responseDTO = new ResponseDTO();

        if (file == null || file.isEmpty()) {
            responseDTO.setStatus(false);
            responseDTO.setMessage("Excel file is required");
            return null;
        }

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columns = new HashMap<>();

            // Read headers
            for (Cell cell : headerRow) {
                String columnName = cell.getStringCellValue().trim();
                columns.put(columnName, cell.getColumnIndex());
            }

            // Read DTO fields
            Field[] fields = excelDTO.getDeclaredFields();

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                T dto = excelDTO.getDeclaredConstructor().newInstance();

                for (Field field : fields) {
                    ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                    if (annotation == null) {
                        continue;
                    }

                    String columnName = annotation.value();
                    Integer columnIndex = columns.get(columnName);

                    if (columnIndex == null) {
                        continue;
                    }

                    Cell cell = row.getCell(columnIndex);
                    Object value = convertCellValue(cell, field.getType());

                    field.setAccessible(true);
                    field.set(dto, value);
                }

                result.add(dto);
            }
        } catch (IOException | NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
        return result;
    }

    private Object convertCellValue(Cell cell, Class<?> targetType) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {

            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                if (targetType == Integer.class || targetType == int.class) {
                    yield (int) value;
                }

                if (targetType == Long.class || targetType == long.class) {
                    yield (long) value;
                }

                if (targetType == BigDecimal.class) {
                    yield BigDecimal.valueOf(value);
                }
                yield value;
            }

            default -> null;
        };
    }
}
