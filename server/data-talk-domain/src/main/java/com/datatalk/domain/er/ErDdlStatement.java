package com.datatalk.domain.er;

public record ErDdlStatement(String sql, ErDdlKind kind, String table) {}
