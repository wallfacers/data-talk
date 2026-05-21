package com.datatalk.application.stage;

import java.util.Optional;

@FunctionalInterface
public interface ConnectionIdProvider {
    Optional<String> currentConnectionId();
}
