package com.datatalk.application.opencode;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bi-directional map between DataTalk session ids and OpenCode session ids. */
@Component
public class OpenCodeSessionMap {

    private final Map<String, String> dtToOc = new ConcurrentHashMap<>();
    private final Map<String, String> ocToDt = new ConcurrentHashMap<>();

    public void bind(String dataTalkSessionId, String openCodeSessionId) {
        dtToOc.put(dataTalkSessionId, openCodeSessionId);
        ocToDt.put(openCodeSessionId, dataTalkSessionId);
    }

    public String openCodeFor(String dataTalkSessionId) {
        return dtToOc.get(dataTalkSessionId);
    }

    public String dataTalkFor(String openCodeSessionId) {
        return ocToDt.get(openCodeSessionId);
    }

    public void unbind(String dataTalkSessionId) {
        String oc = dtToOc.remove(dataTalkSessionId);
        if (oc != null) ocToDt.remove(oc);
    }
}
