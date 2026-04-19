package com.datatalk.application.session;

import com.datatalk.application.persistence.SessionRecord;

/** Result of SessionService.create — carries the record plus a flag indicating
 *  whether an existing empty session was reused instead of creating a new one. */
public record CreateSessionResult(SessionRecord record, boolean reusedEmpty) {}
