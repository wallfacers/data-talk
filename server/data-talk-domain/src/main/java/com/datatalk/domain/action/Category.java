package com.datatalk.domain.action;

/**
 * Functional category of an action. Drives front-end grouping
 * (e.g. METADATA actions are merged into a ContextToolGroup) and
 * visual variants (QUESTION has its own layout, not a risk-colored card).
 */
public enum Category {
    METADATA,  // describe_table / list_tables / show_schema — merge into ContextToolGroup
    QUERY,     // execute_sql — read-only data access
    MUTATION,  // preview_sql — DML preview + confirm
    ARTIFACT,  // artifact_created / pin / supersede
    DDL,       // reserved for future schema-changing actions
    QUESTION,  // AI → user interactive question (independent visual)
    MISC       // default / unclassified
}