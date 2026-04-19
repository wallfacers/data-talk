package com.datatalk.application.ai;

import com.datatalk.dto.AiModelDto;
import com.datatalk.dto.AiModelsDto;
import com.datatalk.dto.AiProviderDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public JsonNode listProviders() {
        JsonNode root = oc.listProviders();
        Set<String> configured = readConfiguredProviders();
        // Merge auth.json configured providers into connected array
        if (root.has("connected")) {
            root.get("connected").forEach(n -> configured.add(n.asText()));
        }
        // Build a new JsonNode with the merged connected array
        var merged = om.createObjectNode();
        if (root.has("all")) merged.set("all", root.get("all"));
        var connectedArray = merged.putArray("connected");
        configured.forEach(connectedArray::add);
        return merged;
    }

    /** Reads ~/.local/share/opencode/auth.json and returns provider IDs that have credentials. */
    private Set<String> readConfiguredProviders() {
        Set<String> ids = new LinkedHashSet<>();
        String home = System.getProperty("user.home");
        if (home == null) return ids;
        Path authFile = Path.of(home, ".local", "share", "opencode", "auth.json");
        if (!Files.exists(authFile)) return ids;
        try {
            JsonNode auth = om.readTree(Files.readString(authFile));
            auth.fieldNames().forEachRemaining(ids::add);
        } catch (IOException e) {
            // Silently ignore — auth.json may be locked or malformed
        }
        return ids;
    }

    public JsonNode providerAuth() { return oc.getProviderAuth(); }

    public void putCredentials(String providerId, Map<String, Object> payload) {
        oc.putAuth(providerId, payload);
    }

    public void deleteCredentials(String providerId) {
        oc.deleteAuth(providerId);
        // Also remove from local auth.json so listProviders() reflects the change
        removeFromAuthJson(providerId);
    }

    private void removeFromAuthJson(String providerId) {
        String home = System.getProperty("user.home");
        if (home == null) return;
        Path authFile = Path.of(home, ".local", "share", "opencode", "auth.json");
        if (!Files.exists(authFile)) return;
        try {
            JsonNode auth = om.readTree(Files.readString(authFile));
            if (!auth.has(providerId)) return;
            var updated = om.createObjectNode();
            auth.fieldNames().forEachRemaining(name -> {
                if (!name.equals(providerId)) updated.set(name, auth.get(name));
            });
            Files.writeString(authFile, om.writeValueAsString(updated));
        } catch (IOException e) {
            // Silently ignore — auth.json may be locked or malformed
        }
    }

    public AiModelsDto listModels() {
        JsonNode root = oc.listProviders();
        Set<String> connected = new HashSet<>();
        if (root.has("connected")) root.get("connected").forEach(n -> connected.add(n.asText()));
        // Merge with locally configured providers from auth.json
        connected.addAll(readConfiguredProviders());
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
