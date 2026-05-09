package com.datatalk.application.fileartifact;

public class FileArtifactNotFoundException extends RuntimeException {
    private final String fileArtifactId;
    public FileArtifactNotFoundException(String fileArtifactId, String detail) {
        super("file_artifact not found: " + fileArtifactId + " (" + detail + ")");
        this.fileArtifactId = fileArtifactId;
    }
    public String fileArtifactId() { return fileArtifactId; }
}
