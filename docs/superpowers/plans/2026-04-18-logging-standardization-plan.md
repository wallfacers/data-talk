# Logging Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add logback-spring.xml configuration, fix System.err/log-swallowing code, and add file-based log output.

**Architecture:** Create a Spring-profile-aware logback configuration that outputs colored console logs in dev and structured file+console logs in prod. Fix two Java files to use proper logging instead of System.err.println and silent catch blocks.

**Tech Stack:** Logback, SLF4J, Spring Boot 3.5, Java 21

---

### Task 1: Create logback-spring.xml

**Files:**
- Create: `server/data-talk-adapter/src/main/resources/logback-spring.xml`

- [ ] **Step 1: Create the logback-spring.xml configuration file**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <property name="LOG_PATTERN" value="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n"/>
    <property name="LOG_PATTERN_COLOR" value="%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg%n"/>
    <property name="LOG_DIR" value="logs"/>

    <!-- Console appender (dev, with color) -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN_COLOR}</pattern>
        </encoder>
    </appender>

    <!-- File appender (prod, rolling) -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_DIR}/data-talk.log</file>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_DIR}/archived/data-talk.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>500MB</totalSizeCap>
            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>50MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
        </rollingPolicy>
    </appender>

    <!-- Dev profile: console only -->
    <springProfile name="dev">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        <logger name="com.datatalk" level="DEBUG"/>
        <logger name="org.springframework" level="INFO"/>
        <logger name="org.hibernate" level="WARN"/>
    </springProfile>

    <!-- Prod profile: console + file -->
    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="FILE"/>
        </root>
        <logger name="com.datatalk" level="DEBUG"/>
        <logger name="org.springframework" level="INFO"/>
        <logger name="org.hibernate" level="WARN"/>
    </springProfile>

    <!-- Default (no profile specified): same as dev -->
    <springProfile name="default">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        <logger name="com.datatalk" level="DEBUG"/>
        <logger name="org.springframework" level="INFO"/>
        <logger name="org.hibernate" level="WARN"/>
    </springProfile>

</configuration>
```

- [ ] **Step 2: Remove duplicate logging.level from application.yml**

Edit `server/data-talk-adapter/src/main/resources/application.yml`, delete lines 33-35:

```yaml
# DELETE these lines - now in logback-spring.xml
logging:
  level:
    com.datatalk: DEBUG
```

After removal, the file ends at line 32 with `port: 8080` and continues with `datatalk:` config.

- [ ] **Step 3: Verify compilation**

Run: `cd server && mvn compile -q`
Expected: zero errors

- [ ] **Step 4: Commit**

```bash
git add server/data-talk-adapter/src/main/resources/logback-spring.xml
git add server/data-talk-adapter/src/main/resources/application.yml
git commit -m "feat(logging): add logback-spring.xml with dev/prod profiles and file rolling"
```

---

### Task 2: Fix System.err.println in OpenCodeGatewayBeans

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java:108,115,120`

- [ ] **Step 1: Replace System.err.println with log.error**

In `OpenCodeGatewayBeans.java`, change the `registerOnStartup` method. Replace the entire method body:

```java
    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        if (!serveProps.isEnabled()) {
            log.info("OpenCode embedded server is disabled - skipping tool registration");
            return;
        }

        if (!processManager.isRunning()) {
            log.error("OpenCode embedded server failed to start - skipping tool registration (degraded mode)");
            return;
        }

        try {
            gateway.registerTools();
        } catch (Exception e) {
            log.error("OpenCode tool registration failed (degraded mode): {}", e.getMessage(), e);
        }
        try {
            eventLoop.start();
        } catch (Exception e) {
            log.error("OpenCode SSE event loop failed to start (degraded mode): {}", e.getMessage(), e);
        }
    }
```

Key changes:
- Line 108: `System.err.println(...)` → `log.error(...)`
- Line 115: `System.err.println(...)` → `log.error(...)` with exception cause
- Line 120: `System.err.println(...)` → `log.error(...)` with exception cause

- [ ] **Step 2: Verify compilation**

Run: `cd server && mvn compile -q`
Expected: zero errors

- [ ] **Step 3: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java
git commit -m "fix(logging): replace System.err.println with log.error in OpenCodeGatewayBeans"
```

---

### Task 3: Fix silent exception swallowing in SessionBus

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionBus.java:137-139`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionBusTest.java`

- [ ] **Step 1: Add logging to the silent catch block**

In `SessionBus.java`, change line 137-139 from:

```java
        } catch (Throwable t) {
            // swallow to keep the flusher alive
        }
```

to:

```java
        } catch (Throwable t) {
            log.error("Event flusher failed for session={}", sessionId, t);
        }
```

- [ ] **Step 2: Add test for flusher error logging**

In `SessionBusTest.java`, add a new test. Import `org.slf4j.LoggerFactory` and `ch.qos.logback.classic.Logger` at the top:

```java
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
```

Add this test class (as a nested class or separate file — use a separate test file for clarity):

Create: `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionBusFlusherErrorTest.java`

```java
package com.datatalk.application.session;

import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SessionBusFlusherErrorTest {

    SessionBus bus;
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        // Attach a ListAppender to SessionBus logger to capture log output
        Logger logger = (Logger) LoggerFactory.getLogger(SessionBus.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (bus != null) bus.close();
        Logger logger = (Logger) LoggerFactory.getLogger(SessionBus.class);
        logger.detachAppender(listAppender);
    }

    @Test
    void flusherLogsErrorWhenPersisterThrowsRuntimeException() {
        SessionBus.Persister brokenPersister = (sid, eid, type, payload, ts) -> {
            throw new RuntimeException("database locked");
        };

        bus = new SessionBus("s-err", 0L, 500, Duration.ofMinutes(5),
            Duration.ofMillis(16), clock, new ObjectMapper(), brokenPersister);
        bus.subscribe("c-1", 0L, n -> {});

        // publish an event - persister will throw but bus should survive
        bus.publish(new DtEvent.Heartbeat(1000L));

        // wait for flush cycle and verify error was logged
        await().atMost(Duration.ofSeconds(2)).until(() ->
            listAppender.list.stream().anyMatch(e ->
                e.getFormattedMessage().contains("database locked")));

        // bus should still be alive - publish another event
        bus.publish(new DtEvent.Heartbeat(2000L));
        await().atMost(Duration.ofSeconds(2)).until(() ->
            listAppender.list.stream().filter(e ->
                e.getFormattedMessage().contains("database locked")).count() >= 2);
    }
}
```

- [ ] **Step 3: Run the new test**

Run: `cd server && mvn test -pl data-talk-application -Dtest=SessionBusFlusherErrorTest -q`
Expected: PASS

- [ ] **Step 4: Run existing SessionBusTest to verify no regression**

Run: `cd server && mvn test -pl data-talk-application -Dtest=SessionBusTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/session/SessionBus.java
git add server/data-talk-application/src/test/java/com/datatalk/application/session/SessionBusFlusherErrorTest.java
git commit -m "fix(logging): log error instead of silently swallowing flusher exceptions"
```

---

### Task 4: Add logs directory to .gitignore and verify full build

**Files:**
- Modify: `server/.gitignore` (or root `.gitignore`)

- [ ] **Step 1: Ensure logs/ directory is in .gitignore**

Check root `.gitignore`. If `logs/` or `*.log` is not present, add:

```
# Application logs
logs/
*.log
```

- [ ] **Step 2: Run full backend build**

Run: `cd server && mvn clean verify -q`
Expected: all tests pass, zero compilation errors

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git commit -m "chore: add logs/ directory to gitignore"
```
