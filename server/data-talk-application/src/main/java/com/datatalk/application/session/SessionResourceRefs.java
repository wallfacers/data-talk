package com.datatalk.application.session;

import java.util.List;

/**
 * Holds references to resources associated with a session, collected before
 * session deletion so that the FK {@code ON DELETE SET NULL} does not lose
 * the linkage information.
 *
 * @param sessionId  the session being deleted
 * @param exportIds  export IDs from {@link com.datatalk.application.importexport.DataExportService}
 *                   whose origin session is this session
 * @param uploadIds  IDs of rows in the {@code uploaded_file} table whose
 *                   {@code session_id} points to this session
 */
public record SessionResourceRefs(
        String sessionId,
        List<String> exportIds,
        List<String> uploadIds
) {}
