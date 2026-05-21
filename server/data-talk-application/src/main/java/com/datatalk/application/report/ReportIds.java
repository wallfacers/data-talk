package com.datatalk.application.report;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Id generators for report-related artifacts.
 *
 * <p>Format mirrors {@code DashboardIds}: short prefix + 8 hex chars.
 */
public final class ReportIds {

    private static final SecureRandom RNG = new SecureRandom();

    private ReportIds() {}

    public static String newReportId() {
        return "r-" + randomHex();
    }

    public static String newGroupId() {
        return "g-" + randomHex();
    }

    public static String newReportAssetId(String reportId, String blockId) {
        return reportId + ":chart-" + blockId;
    }

    public static String newReportCsvId(String reportId, int index) {
        return reportId + ":csv-" + index;
    }

    private static String randomHex() {
        byte[] b = new byte[4];
        RNG.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
