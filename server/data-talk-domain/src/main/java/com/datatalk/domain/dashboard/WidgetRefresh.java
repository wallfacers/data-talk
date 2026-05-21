package com.datatalk.domain.dashboard;

public record WidgetRefresh(Integer intervalMs, Widget.RefreshStrategy strategy) {
}
