package com.datatalk.chatdb.service;

import com.datatalk.chatdb.model.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private final JdbcTemplate jdbcTemplate;

    public QueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行查询。MVP 阶段固定查 H2 演示库的 users 表。
     * connectionId 固定为 "demo"，sql 参数被忽略，使用硬编码 SQL。
     */
    public QueryResponse executeQuery(String connectionId, String sql) {
        long start = System.currentTimeMillis();

        if (!"demo".equals(connectionId)) {
            throw new IllegalArgumentException(
                "MVP only supports 'demo' connection, got: " + connectionId);
        }

        String demoSql = "SELECT id, name, email, created_at FROM users ORDER BY id";
        log.debug("Executing demo SQL: {}", demoSql);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(demoSql);
        long duration = System.currentTimeMillis() - start;

        // Normalize column names to lowercase
        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalizedRow = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                normalizedRow.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            normalizedRows.add(normalizedRow);
        }

        List<String> columns = normalizedRows.isEmpty()
                ? List.of("id", "name", "email", "created_at")
                : new ArrayList<>(normalizedRows.get(0).keySet());

        return new QueryResponse(columns, normalizedRows, duration);
    }
}
