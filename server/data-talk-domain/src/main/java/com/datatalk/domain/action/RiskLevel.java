package com.datatalk.domain.action;

/**
 * Front-end risk color coding for tool UI cards. See product spec
 * {@code 2026-04-19-ai-message-rendering-migration-design.md} §3.4, §5.3.
 */
public enum RiskLevel {
    L1,  // Safe: metadata / read-only (green)
    L2,  // Mutation requiring confirmation (yellow)
    L3   // Destructive / high-impact (red)
}