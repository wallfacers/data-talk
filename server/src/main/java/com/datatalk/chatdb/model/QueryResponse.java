package com.datatalk.chatdb.model;

import java.util.List;
import java.util.Map;

public class QueryResponse {

    private List<String> columns;
    private List<Map<String, Object>> rows;
    private long durationMs;
    private int rowCount;

    public QueryResponse() {}

    public QueryResponse(List<String> columns, List<Map<String, Object>> rows, long durationMs) {
        this.columns = columns;
        this.rows = rows;
        this.durationMs = durationMs;
        this.rowCount = rows.size();
    }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
    public List<Map<String, Object>> getRows() { return rows; }
    public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
}
