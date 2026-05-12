package com.datatalk.application.ingestion.parser;

import java.util.Iterator;
import java.util.Map;

public interface RowStream extends AutoCloseable, Iterator<Map<String, Object>> {
    @Override
    void close();
}
