package com.datatalk.domain.error;

public final class DataTalkErrorCodes {
    private DataTalkErrorCodes() {}

    public static final String SCHEMA_INPUT_INVALID          = "schema.input_invalid";
    public static final String SCHEMA_OUTPUT_INVALID         = "schema.output_invalid";
    public static final String CONNECTION_MISSING            = "connection.missing";
    public static final String CONNECTION_UNREACHABLE        = "connection.unreachable";
    public static final String SQL_SYNTAX_ERROR              = "sql.syntax_error";
    public static final String SQL_TIMEOUT                   = "sql.timeout";
    public static final String SQL_FORBIDDEN                 = "sql.forbidden";
    public static final String ARTIFACT_SUPERSEDES_NOT_FOUND = "artifact.supersedes_not_found";
    public static final String ARTIFACT_TOO_LARGE            = "artifact.too_large";
    public static final String ACTION_TIMEOUT                = "action.timeout";
    public static final String ACTION_CANCELLED              = "action.cancelled";
    public static final String CLIENT_ACTION_UNREACHABLE     = "client_action.unreachable";
    public static final String UPSTREAM_UNAVAILABLE          = "upstream.unavailable";
    public static final String CHANNEL_RESUME_OUT_OF_WINDOW  = "channel.resume_out_of_window";
}
