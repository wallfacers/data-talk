package com.datatalk.application.dashboard;

import java.security.SecureRandom;

public final class DashboardIds {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private DashboardIds() {}

    public static String newDashboardId() {
        StringBuilder sb = new StringBuilder("dash_");
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
