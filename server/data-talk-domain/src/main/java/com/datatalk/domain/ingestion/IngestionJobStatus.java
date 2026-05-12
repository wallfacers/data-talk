package com.datatalk.domain.ingestion;

public sealed interface IngestionJobStatus
    permits IngestionJobStatus.Pending, IngestionJobStatus.Fetching,
            IngestionJobStatus.Fetched, IngestionJobStatus.Mapping,
            IngestionJobStatus.AwaitingConfirm, IngestionJobStatus.Writing,
            IngestionJobStatus.Completed, IngestionJobStatus.Failed,
            IngestionJobStatus.Cancelled {
    record Pending() implements IngestionJobStatus {}
    record Fetching() implements IngestionJobStatus {}
    record Fetched() implements IngestionJobStatus {}
    record Mapping() implements IngestionJobStatus {}
    record AwaitingConfirm() implements IngestionJobStatus {}
    record Writing() implements IngestionJobStatus {}
    record Completed() implements IngestionJobStatus {}
    record Failed(String message) implements IngestionJobStatus {}
    record Cancelled() implements IngestionJobStatus {}

    static String toCode(IngestionJobStatus s) {
        return switch (s) {
            case Pending p -> "pending";
            case Fetching f -> "fetching";
            case Fetched f -> "fetched";
            case Mapping m -> "mapping";
            case AwaitingConfirm a -> "awaiting_confirm";
            case Writing w -> "writing";
            case Completed c -> "completed";
            case Failed f -> "failed";
            case Cancelled c -> "cancelled";
        };
    }

    static IngestionJobStatus fromCode(String code, String errorMessage) {
        return switch (code) {
            case "pending" -> new Pending();
            case "fetching" -> new Fetching();
            case "fetched" -> new Fetched();
            case "mapping" -> new Mapping();
            case "awaiting_confirm" -> new AwaitingConfirm();
            case "writing" -> new Writing();
            case "completed" -> new Completed();
            case "failed" -> new Failed(errorMessage == null ? "" : errorMessage);
            case "cancelled" -> new Cancelled();
            default -> throw new IllegalArgumentException("unknown ingestion status: " + code);
        };
    }
}
