package com.datatalk.application.preference;

import com.datatalk.domain.preference.UserPreferences;

public interface UserPreferencesRepository {
    UserPreferences findPreferences();
    void save(UserPreferences preferences);
}
