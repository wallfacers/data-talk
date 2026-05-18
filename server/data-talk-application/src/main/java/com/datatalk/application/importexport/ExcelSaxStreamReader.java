package com.datatalk.application.importexport;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.Styles;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class ExcelSaxStreamReader {

    private static final Logger log = LoggerFactory.getLogger(ExcelSaxStreamReader.class);

    record StreamReadResult(int totalRows, List<String> columns, List<Map<String, Object>> sampleRows) {}

    StreamReadResult stream(Path file, int batchSize, Consumer<List<Map<String, Object>>> batchConsumer,
                            Map<String, String> columnMappings, Map<String, String> columnTypes) {
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> sampleRows = new ArrayList<>();
        List<Map<String, Object>> batch = new ArrayList<>(batchSize);
        int[] totalRows = {0};
        boolean[] headerProcessed = {false};

        OPCPackage pkg = null;
        try {
            pkg = OPCPackage.open(file.toFile(), PackageAccess.READ);
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sharedStrings = reader.getSharedStringsTable();
            Styles styles = reader.getStylesTable();

            XSSFSheetXMLHandler.SheetContentsHandler unusedHandler = new XSSFSheetXMLHandler.SheetContentsHandler() {
                @Override public void startRow(int rowNum) {}
                @Override public void endRow(int rowNum) {}
                @Override public void cell(String cellReference, String formattedValue, XSSFComment comment) {}
                @Override public void headerFooter(String text, boolean isHeader, String tagName) {}
            };

            Iterator<InputStream> sheets = reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream sheetStream = sheets.next()) {
                    // Use our own handler for row-by-row processing
                    XSSFSheetXMLHandler handler = new XSSFSheetXMLHandler(
                        styles, sharedStrings,
                        new DataTableContentsHandler(columns, sampleRows, batch, batchConsumer,
                                                     totalRows, headerProcessed, columnMappings, columnTypes),
                        false
                    );

                    XMLReader xmlReader = XMLReaderFactory.createXMLReader();
                    xmlReader.setContentHandler(handler);
                    xmlReader.parse(new InputSource(sheetStream));
                }
                // Only process the first sheet
                break;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read Excel file: " + e.getMessage(), e);
        } finally {
            if (pkg != null) {
                try {
                    pkg.close();
                } catch (Exception e) {
                    log.warn("Failed to close OPCPackage", e);
                }
            }
        }

        // Flush remaining batch
        if (!batch.isEmpty()) {
            batchConsumer.accept(batch);
        }

        log.info("Excel stream read complete: {} rows, {} columns", totalRows[0], columns.size());
        return new StreamReadResult(totalRows[0], columns, sampleRows);
    }

    /**
     * Custom SheetContentsHandler that collects rows into maps.
     */
    private static class DataTableContentsHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final List<String> columns;
        private final List<Map<String, Object>> sampleRows;
        private final List<Map<String, Object>> batch;
        private final Consumer<List<Map<String, Object>>> batchConsumer;
        private final int[] totalRows;
        private final boolean[] headerProcessed;
        private final Map<String, String> columnMappings;
        private final Map<String, String> columnTypes;

        private List<String> currentRowValues = new ArrayList<>();
        private boolean isHeaderRow = true;

        DataTableContentsHandler(List<String> columns, List<Map<String, Object>> sampleRows,
                                 List<Map<String, Object>> batch,
                                 Consumer<List<Map<String, Object>>> batchConsumer,
                                 int[] totalRows, boolean[] headerProcessed,
                                 Map<String, String> columnMappings,
                                 Map<String, String> columnTypes) {
            this.columns = columns;
            this.sampleRows = sampleRows;
            this.batch = batch;
            this.batchConsumer = batchConsumer;
            this.totalRows = totalRows;
            this.headerProcessed = headerProcessed;
            this.columnMappings = columnMappings;
            this.columnTypes = columnTypes;
        }

        @Override
        public void startRow(int rowNum) {
            currentRowValues = new ArrayList<>();
        }

        @Override
        public void endRow(int rowNum) {
            if (!headerProcessed[0]) {
                // First row = header
                for (String rawCol : currentRowValues) {
                    String mapped = (columnMappings != null && columnMappings.containsKey(rawCol))
                        ? columnMappings.get(rawCol) : rawCol;
                    columns.add(mapped);
                }
                headerProcessed[0] = true;
                isHeaderRow = false;
                return;
            }

            // Data row
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                String rawValue = i < currentRowValues.size() ? currentRowValues.get(i) : null;
                Object value = convertValue(rawValue, columns.get(i), columnTypes);
                row.put(columns.get(i), value);
            }

            if (totalRows[0] < 3) {
                sampleRows.add(new LinkedHashMap<>(row));
            }
            batch.add(row);
            totalRows[0]++;

            if (batch.size() >= 1000) {
                batchConsumer.accept(new ArrayList<>(batch));
                batch.clear();
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            currentRowValues.add(formattedValue != null ? formattedValue : "");
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {}
    }

    private static Object convertValue(String rawValue, String columnName, Map<String, String> columnTypes) {
        if (columnTypes != null && columnTypes.containsKey(columnName)) {
            return (rawValue == null || rawValue.isEmpty()) ? null : rawValue;
        }

        if (rawValue == null || rawValue.isEmpty()) {
            return null;
        }

        // Try Long
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ignored) {}

        // Try Double
        try {
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException ignored) {}

        return rawValue;
    }

    /**
     * Infers DDL column types from sample rows.
     */
    List<String> inferDdlTypes(List<String> columns, List<Map<String, Object>> sampleRows,
                               Map<String, String> columnTypes) {
        List<String> ddlTypes = new ArrayList<>();
        for (String col : columns) {
            if (columnTypes != null && columnTypes.containsKey(col)) {
                ddlTypes.add(columnTypes.get(col));
                continue;
            }
            String inferred = "VARCHAR(255)";
            for (Map<String, Object> row : sampleRows) {
                Object val = row.get(col);
                if (val == null) continue;
                if (val instanceof Long || val instanceof Integer) {
                    inferred = "BIGINT";
                    break;
                }
                if (val instanceof Double || val instanceof Float) {
                    inferred = "DOUBLE";
                    break;
                }
                if (val instanceof Boolean) {
                    inferred = "BOOLEAN";
                    break;
                }
            }
            ddlTypes.add(inferred);
        }
        return ddlTypes;
    }
}
