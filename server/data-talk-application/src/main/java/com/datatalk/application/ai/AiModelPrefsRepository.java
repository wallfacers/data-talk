package com.datatalk.application.ai;

import java.util.Set;

public interface AiModelPrefsRepository {
    /** Returns "providerId/modelId" format disabled set. Not in set = enabled by default. */
    Set<String> disabledSet();

    void setEnabled(String providerId, String modelId, boolean enabled);
}
