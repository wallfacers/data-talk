package com.datatalk.infra.preference;

import com.datatalk.application.preference.UserPreferencesRepository;
import com.datatalk.domain.preference.UserPreferences;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;

@Repository
public class UserPreferencesRepositoryJdbc implements UserPreferencesRepository {

    private static final String ID = "default";
    private final JdbcTemplate jdbc;

    public UserPreferencesRepositoryJdbc(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserPreferences findPreferences() {
        var list = jdbc.query(
            "SELECT id, timezone, date_format, updated_at FROM user_preferences WHERE id = ?",
            (rs, i) -> new UserPreferences(
                rs.getString("id"),
                ZoneId.of(rs.getString("timezone")),
                rs.getString("date_format"),
                rs.getLong("updated_at")
            ), ID);
        return list.isEmpty() ? UserPreferences.DEFAULT : list.get(0);
    }

    @Override
    public void save(UserPreferences preferences) {
        jdbc.update(
            "UPDATE user_preferences SET timezone = ?, date_format = ?, updated_at = ? WHERE id = ?",
            preferences.timezone().getId(),
            preferences.dateFormat(),
            preferences.updatedAt(),
            preferences.id()
        );
    }
}
