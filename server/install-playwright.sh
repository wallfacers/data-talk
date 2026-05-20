#!/usr/bin/env bash
set -euo pipefail

echo "=== Installing Playwright Chromium browser ==="
cd "$(dirname "$0")/data-talk-infrastructure"

mvn exec:java \
  -D exec.mainClass=com.microsoft.playwright.CLI \
  -D exec.args="install chromium" \
  -q

echo "=== Playwright Chromium installed successfully ==="
echo ""
echo "Now run: cd .. && mvn spring-boot:run -pl data-talk-adapter"
