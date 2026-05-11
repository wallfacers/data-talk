#!/usr/bin/env python3
"""bezel HTML compliance validator.

Run before promoting a generated dashboard.html. Exits 0 on pass, 1 on fail.
"""
from __future__ import annotations
import argparse
import re
import sys
from pathlib import Path

ALLOWED_CDN = ("https://cdn.jsdelivr.net/",)

REQUIRED_CHECKS = [
    ("csp_meta", r'<meta\s+http-equiv\s*=\s*["\']Content-Security-Policy["\']'),
    ("bezel_origin_placeholder", r"__BEZEL_SERVER_ORIGIN__"),
    ("frame_ancestors_self", r"frame-ancestors\s+'self'"),
    ("json_hash_meta", r'<meta\s+name\s*=\s*["\']__JSON_HASH__["\']'),
    ("bezel_config", r"window\.__BEZEL_CONFIG__\s*="),
]

FORBIDDEN_CHECKS = [
    ("inline_event_handler", r"\bon[a-z]+\s*="),
    ("unsafe_eval", r"unsafe-eval"),
    ("wildcard_in_default_src", r"default-src[^;]*\*"),
]

def check_script_src_whitelist(html: str) -> list[str]:
    errors = []
    for m in re.finditer(r'<script[^>]+src\s*=\s*["\']([^"\']+)["\']', html, re.I):
        src = m.group(1)
        if not any(src.startswith(p) for p in ALLOWED_CDN):
            errors.append(f"non-whitelisted script src: {src}")
    return errors

def check_fetch_url_shape(html: str) -> list[str]:
    """Heuristic: every fetch() URL should target /api/dashboards/.../widgets/.../data."""
    errors = []
    for m in re.finditer(r"fetch\s*\(\s*['\"`]([^'\"`]+)['\"`]", html):
        url = m.group(1)
        if not re.match(r"^/api/dashboards/[^/]+/widgets/[^/]+/data$", url) and "{" not in url:
            errors.append(f"suspicious fetch URL: {url}")
    return errors

def validate(html_path: Path) -> int:
    html = html_path.read_text(encoding="utf-8")
    failures: list[str] = []

    for name, pattern in REQUIRED_CHECKS:
        if not re.search(pattern, html, re.I):
            failures.append(f"missing required: {name} (/{pattern}/)")

    for name, pattern in FORBIDDEN_CHECKS:
        if re.search(pattern, html, re.I):
            failures.append(f"forbidden present: {name} (/{pattern}/)")

    failures.extend(check_script_src_whitelist(html))
    failures.extend(check_fetch_url_shape(html))

    if failures:
        print(f"[bezel.validate] FAIL: {html_path}", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1

    print(f"[bezel.validate] PASS: {html_path}")
    return 0

def main() -> int:
    ap = argparse.ArgumentParser(description="Validate a bezel-generated dashboard.html")
    ap.add_argument("html", type=Path, help="path to dashboard.html")
    args = ap.parse_args()
    return validate(args.html)

if __name__ == "__main__":
    sys.exit(main())
