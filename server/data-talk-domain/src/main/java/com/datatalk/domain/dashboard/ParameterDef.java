package com.datatalk.domain.dashboard;

public record ParameterDef(String id, String scope, String ownerWidgetId, String name, String type, Object defaultValue) {}
