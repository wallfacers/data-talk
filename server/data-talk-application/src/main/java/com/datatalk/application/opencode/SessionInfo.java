package com.datatalk.application.opencode;

/**
 * OpenCode 的 session 对象最小视图。对应 OpenCode {@code session.*} 事件 payload 里的 {@code info} 节点。
 */
public record SessionInfo(String id, String title, long version) {}