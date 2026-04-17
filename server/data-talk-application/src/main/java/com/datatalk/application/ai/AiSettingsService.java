package com.datatalk.application.ai;

import com.datatalk.dto.AiModelDto;
import com.datatalk.dto.AiModelsDto;
import com.datatalk.dto.AiProviderDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiSettingsService {

    private final OpenCodeProviderClient oc;
    private final AiModelPrefsRepository modelPrefs;
    private final AiUserPrefsRepository userPrefs;
    private final ObjectMapper om;

    public AiSettingsService(OpenCodeProviderClient oc,
                             AiModelPrefsRepository modelPrefs,
                             AiUserPrefsRepository userPrefs,
                             ObjectMapper om) {
        this.oc = oc;
        this.modelPrefs = modelPrefs;
        this.userPrefs = userPrefs;
        this.om = om;
    }

    public JsonNode listProviders() { return oc.listProviders(); }

    public JsonNode providerAuth() { return oc.getProviderAuth(); }

    public void putCredentials(String providerId, Map<String, Object> payload) {
        oc.putAuth(providerId, payload);
    }

    public AiModelsDto listModels() {
        JsonNode root = oc.listProviders();
        Set<String> connected = new HashSet<>();
        if (root.has("connected")) root.get("connected").forEach(n -> connected.add(n.asText()));
        Set<String> disabled = modelPrefs.disabledSet();

        List<AiProviderDto> providers = new ArrayList<>();
        if (root.has("all")) {
            for (JsonNode p : root.get("all")) {
                String pid = p.get("id").asText();
                String name = p.has("name") ? p.get("name").asText() : pid;
                List<AiModelDto> models = new ArrayList<>();
                JsonNode m = p.get("models");
                if (m != null && m.isObject()) {
                    m.fields().forEachRemaining(e -> {
                        String mid = e.getKey();
                        String mname = e.getValue().has("name") ? e.getValue().get("name").asText() : mid;
                        boolean enabled = !disabled.contains(pid + "/" + mid);
                        models.add(new AiModelDto(mid, mname, enabled));
                    });
                }
                providers.add(new AiProviderDto(pid, name, connected.contains(pid), models));
            }
        }
        return new AiModelsDto(providers);
    }

    public void setModelEnabled(String providerId, String modelId, boolean enabled) {
        modelPrefs.setEnabled(providerId, modelId, enabled);
    }

    public String getCurrentModel() { return userPrefs.getCurrentModel(); }
    public void setCurrentModel(String modelId) { userPrefs.setCurrentModel(modelId); }
}
