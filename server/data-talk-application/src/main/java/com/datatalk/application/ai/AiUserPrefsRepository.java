package com.datatalk.application.ai;

public interface AiUserPrefsRepository {
    String getCurrentModel();
    void setCurrentModel(String modelId);
}
