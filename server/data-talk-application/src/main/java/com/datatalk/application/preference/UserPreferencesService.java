package com.datatalk.application.preference;

import com.datatalk.domain.preference.UserPreferences;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

@Service
public class UserPreferencesService {

    private final UserPreferencesRepository repository;

    public UserPreferencesService(UserPreferencesRepository repository) {
        this.repository = repository;
    }

    public UserPreferences getPreferences() {
        return repository.findPreferences();
    }

    public UserPreferences updateTimeZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("timezone is required");
        }
        ZoneId zone = ZoneId.of(zoneId);
        UserPreferences current = repository.findPreferences();
        UserPreferences updated = new UserPreferences(
            current.id(), zone, current.dateFormat(), System.currentTimeMillis()
        );
        repository.save(updated);
        return updated;
    }

    public UserPreferences updateDateFormat(String dateFormat) {
        if (dateFormat == null || dateFormat.isBlank()) {
            throw new IllegalArgumentException("dateFormat is required");
        }
        UserPreferences current = repository.findPreferences();
        UserPreferences updated = new UserPreferences(
            current.id(), current.timezone(), dateFormat, System.currentTimeMillis()
        );
        repository.save(updated);
        return updated;
    }
}
