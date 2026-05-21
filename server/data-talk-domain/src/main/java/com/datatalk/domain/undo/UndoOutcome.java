package com.datatalk.domain.undo;

public sealed interface UndoOutcome permits UndoOutcome.Captured, UndoOutcome.NotUndoable, UndoOutcome.Skipped {

    record Captured(UndoCapture capture) implements UndoOutcome {}

    record NotUndoable(String reason) implements UndoOutcome {}

    record Skipped(String statementType) implements UndoOutcome {}
}
