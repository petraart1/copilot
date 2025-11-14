package com.copilot.auth.service;

import com.copilot.auth.dto.request.UserImportRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class UserImportParser {

    public List<UserImportRow> parseFile(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("Имя файла не может быть пустым");
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        
        return switch (extension) {
            case "csv" -> parseCsv(file);
            case "xlsx", "xls" -> parseExcel(file);
            default -> throw new IllegalArgumentException("Неподдерживаемый формат файла: " + extension);
        };
    }

    private List<UserImportRow> parseCsv(MultipartFile file) throws Exception {
        List<UserImportRow> rows = new ArrayList<>();
        
        try (InputStream is = file.getInputStream();
             java.io.BufferedReader reader = new java.io.BufferedReader(
                     new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            
            String line = reader.readLine();
            if (line == null) {
                throw new IllegalArgumentException("Файл пуст");
            }
            
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    String[] values = parseCsvLine(line);
                    if (values.length < 1 || values[0].trim().isEmpty()) {
                        continue;
                    }
                    
                    UserImportRow row = new UserImportRow(
                            getValue(values, 0, ""),
                            getValue(values, 1, null),
                            getValue(values, 2, null),
                            getValue(values, 3, null),
                            getValue(values, 4, null),
                            getValue(values, 5, null),
                            getValue(values, 6, "EMPLOYEE")
                    );
                    rows.add(row);
                } catch (Exception e) {
                    log.warn("Ошибка при парсинге строки {}: {}", lineNumber, e.getMessage());
                    throw new IllegalArgumentException("Ошибка в строке " + lineNumber + ": " + e.getMessage());
                }
            }
        }
        
        return rows;
    }

    private List<UserImportRow> parseExcel(MultipartFile file) throws Exception {
        List<UserImportRow> rows = new ArrayList<>();
        
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) {
                throw new IllegalArgumentException("Файл должен содержать хотя бы одну строку данных (кроме заголовка)");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                
                String email = getCellValue(row, 0);
                if (email == null || email.trim().isEmpty()) {
                    continue;
                }
                
                UserImportRow importRow = new UserImportRow(
                        email.trim(),
                        getCellValue(row, 1),
                        getCellValue(row, 2),
                        getCellValue(row, 3),
                        getCellValue(row, 4),
                        getCellValue(row, 5),
                        getCellValueOrDefault(row, 6, "EMPLOYEE")
                );
                rows.add(importRow);
            }
        }
        
        return rows;
    }

    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        
        return values.toArray(new String[0]);
    }

    private String getValue(String[] values, int index, String defaultValue) {
        if (index < values.length && values[index] != null && !values[index].trim().isEmpty()) {
            return values[index].trim();
        }
        return defaultValue;
    }

    private String getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) {
            return null;
        }
        
        CellType cellType = cell.getCellType();
        return switch (cellType) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    yield String.valueOf((long) cell.getNumericCellValue());
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case BLANK -> null;
            default -> null;
        };
    }

    private String getCellValueOrDefault(Row row, int cellIndex, String defaultValue) {
        String value = getCellValue(row, cellIndex);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }
}

