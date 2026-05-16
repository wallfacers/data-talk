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
    ("json_hash_meta", r'<meta\s+name\s*=\s*["\']__JSON_HASH__["\']'),
    ("bezel_config", r"window\.__BEZEL_CONFIG__\s*="),
]

# frame-ancestors is NOT checked — browsers ignore it in <meta> tags and the
# iframe is already sandboxed by the host page.

FORBIDDEN_CHECKS = [
    ("inline_event_handler", r"\bon[a-z]+\s*="),
    ("unsafe_eval", r"unsafe-eval"),
    ("wildcard_in_default_src", r"default-src[^;]*\*"),
    ("frame_ancestors_in_meta", r"frame-ancestors\s+"),
]

def check_script_src_whitelist(html: str) -> list[str]:
    errors = []
    for m in re.finditer(r'<script[^>]+src\s*=\s*["\']([^"\']+)["\']', html, re.I):
        src = m.group(1)
        if not any(src.startswith(p) for p in ALLOWED_CDN):
            errors.append(f"non-whitelisted script src: {src}")
    return errors

REQUIRED_CSS_TOKENS = [
    "--card-radius",
    "--card-bg",
    "--card-border",
    "--card-padding",
    "--card-blur",
    "--motion-duration",
    "--motion-easing",
]

def check_css_tokens(html: str) -> list[str]:
    """Check that all required style tokens are defined in :root."""
    errors = []
    root_match = re.search(r':root\s*\{([^}]+)\}', html, re.DOTALL)
    if not root_match:
        errors.append("no :root CSS block found")
        return errors
    root_css = root_match.group(1)
    for token in REQUIRED_CSS_TOKENS:
        if token not in root_css:
            errors.append(f"missing style token in :root: {token}")
    return errors

def check_fetch_url_shape(html: str) -> list[str]:
    """Heuristic: every fetch() URL should target /api/dashboards/.../widgets/.../data."""
    errors = []
    for m in re.finditer(r"fetch\s*\(\s*['\"`]([^'\"`]+)['\"`]", html):
        url = m.group(1)
        if not re.match(r"^/api/dashboards/[^/]+/widgets/[^/]+/data$", url) and "{" not in url:
            errors.append(f"suspicious fetch URL: {url}")
    return errors

# E_WIDGET_ABSOLUTE_POSITION: regressions where .bezel-widget is taken out of the 12-column grid
# (either via a CSS rule or inline style) cause all widgets to stack in the top-left and overlap.
_BEZEL_WIDGET_CSS_RULE = re.compile(r"\.bezel-widget\b[^{}]*\{([^}]*)\}", re.DOTALL)
_BEZEL_WIDGET_INLINE_DIV = re.compile(
    r'<div\b[^>]*\bclass\s*=\s*["\'][^"\']*\bbezel-widget\b[^"\']*["\'][^>]*>',
    re.IGNORECASE,
)
_INLINE_STYLE_ATTR = re.compile(r'\bstyle\s*=\s*"([^"]*)"|\bstyle\s*=\s*\'([^\']*)\'', re.IGNORECASE)

def check_widget_grid_layout(html: str) -> list[str]:
    """Reject .bezel-widget rules / elements that escape the 12-column CSS Grid."""
    errors = []
    for m in _BEZEL_WIDGET_CSS_RULE.finditer(html):
        body = m.group(1)
        if re.search(r"position\s*:\s*absolute", body, re.IGNORECASE):
            errors.append(
                "E_WIDGET_ABSOLUTE_POSITION: .bezel-widget CSS rule contains `position: absolute` — "
                "widgets must remain grid items (use grid-column / grid-row instead)"
            )
            break
    for m in _BEZEL_WIDGET_INLINE_DIV.finditer(html):
        tag = m.group(0)
        sm = _INLINE_STYLE_ATTR.search(tag)
        if not sm:
            continue
        style = (sm.group(1) or sm.group(2) or "").lower()
        if re.search(r"position\s*:\s*absolute", style):
            errors.append(
                "E_WIDGET_ABSOLUTE_POSITION: a .bezel-widget element has inline `position: absolute` — "
                "remove it and use grid-column / grid-row"
            )
            break
        if re.search(r"\b(?:top|left)\s*:", style) and not re.search(r"grid-(?:column|row)\s*:", style):
            errors.append(
                "E_WIDGET_ABSOLUTE_POSITION: a .bezel-widget element uses inline top/left without grid-column/grid-row — "
                "express placement via 12-column grid coordinates from widget.position"
            )
            break
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
    failures.extend(check_css_tokens(html))
    failures.extend(check_widget_grid_layout(html))

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
