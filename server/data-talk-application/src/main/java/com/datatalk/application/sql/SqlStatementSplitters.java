package com.datatalk.application.sql;

import java.util.List;

public interface SqlStatementSplitters {
    List<String> split(String connectionKind, String sql);
}
