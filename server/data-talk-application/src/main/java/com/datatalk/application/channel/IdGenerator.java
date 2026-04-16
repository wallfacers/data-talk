package com.datatalk.application.channel;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Centralized id generator. Tests replace it with a deterministic stub. */
@Component
public class IdGenerator {
    public String next() {
        return UUID.randomUUID().toString();
    }
}
