package com.datatalk.application.stage;

import java.util.List;
import java.util.Map;

public interface SessionTitleLookup {

    /**
     * Returns title-by-id entries for the supplied session ids.
     * Missing ids are omitted so callers can render a deleted-session fallback.
     */
    Map<String, String> titlesByIds(List<String> sessionIds);
}
