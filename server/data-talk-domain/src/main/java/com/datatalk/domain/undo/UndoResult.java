package com.datatalk.domain.undo;

public sealed interface UndoResult permits UndoResult.RequiresConfirmation, UndoResult.Undone, UndoResult.Expired, UndoResult.AlreadyUndone, UndoResult.NotFound {

    record RequiresConfirmation(String inverseSql, int affectedRows, String tableName) implements UndoResult {}

    record Undone(int affectedRows) implements UndoResult {}

    record Expired() implements UndoResult {}

    record AlreadyUndone() implements UndoResult {}

    record NotFound() implements UndoResult {}
}
