package com.datatalk.adapter.controller;

import com.datatalk.application.preference.UserPreferencesService;
import com.datatalk.domain.preference.UserPreferences;
import com.datatalk.dto.UserPreferencesRequest;
import com.datatalk.dto.UserPreferencesResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RestController
public class PreferencesController {

    private final UserPreferencesService service;

    public PreferencesController(UserPreferencesService service) {
        this.service = service;
    }

    @GetMapping("/api/preferences")
    public UserPreferencesResponse getPreferences() {
        UserPreferences prefs = service.getPreferences();
        return new UserPreferencesResponse(prefs.timezone().getId(), prefs.dateFormat());
    }

    @PutMapping("/api/preferences")
    public ResponseEntity<?> updatePreferences(@RequestBody UserPreferencesRequest request) {
        UserPreferences current = service.getPreferences();
        String newTimezone = request.timezone();
        String newDateFormat = request.dateFormat();

        if (newTimezone != null && !newTimezone.isBlank()) {
            try {
                ZoneId.of(newTimezone);
            } catch (DateTimeException e) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Invalid timezone: " + newTimezone));
            }
            current = service.updateTimeZone(newTimezone);
        }
        if (newDateFormat != null && !newDateFormat.isBlank()) {
            try {
                DateTimeFormatter.ofPattern(newDateFormat);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Invalid date format pattern: " + newDateFormat));
            }
            current = service.updateDateFormat(newDateFormat);
        }
        return ResponseEntity.ok(new UserPreferencesResponse(
            current.timezone().getId(), current.dateFormat()));
    }

    private record ErrorResponse(String error) {}
}
