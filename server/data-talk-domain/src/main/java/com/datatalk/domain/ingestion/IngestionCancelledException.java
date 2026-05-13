package com.datatalk.domain.ingestion;

public class IngestionCancelledException extends RuntimeException {

    private final int rowsCommitted;

    public IngestionCancelledException(int rowsCommitted) {
        super("ingestion cancelled (committed " + rowsCommitted + " rows before stop)");
        this.rowsCommitted = rowsCommitted;
    }

    public int rowsCommitted() {
        return rowsCommitted;
    }
}
