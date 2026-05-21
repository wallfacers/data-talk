package com.datatalk.application.fileartifact;

public class FileArtifactConflictException extends RuntimeException {
    public FileArtifactConflictException(String message) { super(message); }
    public FileArtifactConflictException(String message, Throwable cause) { super(message, cause); }
}
