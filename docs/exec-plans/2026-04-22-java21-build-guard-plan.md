# Java 21 Build Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the backend fail fast with a clear "requires Java 21" message instead of surfacing misleading syntax errors when Maven runs under an older JDK, and correct the docs that were updated using the wrong diagnosis.

**Architecture:** Keep the fix at the Maven parent level so every backend module inherits the same Java version guard. Do not change backend runtime code. Use `maven-enforcer-plugin` during `validate` to reject non-Java-21 environments before compilation, then update the SQL error Markdown plan notes and tracking docs to reflect the actual root cause and verification outcome.

**Tech Stack:** Maven, Spring Boot 3.5, Java 21, docs/exec-plans governance.

---

## File Map

- Modify: `server/pom.xml` — add a parent-level Maven Enforcer rule that requires Java 21 and emits a readable failure message.
- Modify: `AGENTS.md` — clarify that backend Maven commands require `JAVA_HOME` (or default `java`) to point to a Java 21 JDK.
- Modify: `docs/exec-plans/2026-04-22-sql-error-markdown-plan.md` — replace the incorrect "server source is broken" verification note with the real wrong-JDK root cause and successful JDK 21 verification.
- Modify: `docs/exec-plans/index.md` — update the completed summary for `SQL Error Markdown Diagnostics`.
- Modify: `docs/exec-plans/tech-debt-tracker.md` — remove the false P0 entry claiming server Java sources are syntactically broken.

## Tasks

- [x] Reproduce the misleading failure under JDK 8 and verify the same build succeeds under JDK 21.
- [x] Add a parent Maven Java 21 fail-fast rule so future wrong-JDK runs stop at `validate` with a clear message.
- [x] Update the operator-facing docs and prior plan notes to reflect the real root cause.
- [x] Re-run wrong-JDK and JDK21 verification and then mark this plan complete.

## Execution Notes

- Root cause investigation:
  - `mvn -version` in the default shell reported `Java version: 1.8.0_202`, while `server/pom.xml` already declared `<java.version>21</java.version>`.
  - The "server source syntax errors" previously observed were Java 21 syntax being compiled by JDK 8, not broken source files.
  - Before the fix, `bash -lc 'export JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk1.8.0_202; export PATH="$JAVA_HOME/bin:$PATH"; mvn validate -q'` exited `0`, which proved the build had no early Java-version guard.
- Implementation:
  - Added `maven-enforcer-plugin` to the server parent POM so all backend modules fail fast during `validate` unless Maven runs on Java 21.
  - Added a concise backend prerequisite note to `AGENTS.md` so the repo instructions match the actual build requirement.
  - Corrected the `SQL Error Markdown Diagnostics` plan notes and removed the false P0 tech-debt entry that was based on the wrong JDK diagnosis.

## Verification Notes

- Wrong JDK guard:
  - `bash -lc 'export JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk1.8.0_202; export PATH="$JAVA_HOME/bin:$PATH"; mvn validate -q'` → fails with `DataTalk server build requires Java 21. Set JAVA_HOME to a JDK 21 installation before running Maven.`
- Correct JDK build:
  - `bash -lc 'export JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9; export PATH="$JAVA_HOME/bin:$PATH"; mvn compile -q'` → passed.
  - `bash -lc 'export JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9; export PATH="$JAVA_HOME/bin:$PATH"; mvn -q -pl data-talk-application -Dtest=SqlExecuteServiceErrorFormattingTest test'` → passed.
  - `bash -lc 'export JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9; export PATH="$JAVA_HOME/bin:$PATH"; mvn -q -pl data-talk-adapter -am -Dtest=SqlExecuteControllerIT#broken_connection_returns_markdown_diagnostics_for_execution_failure test -Dsurefire.failIfNoSpecifiedTests=false'` → passed.
