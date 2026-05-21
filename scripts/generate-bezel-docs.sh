#!/usr/bin/env bash
# generate-bezel-docs.sh — Generate AI-readable reference docs from pattern-catalog.yaml
#
# Usage: ./scripts/generate-bezel-docs.sh
#
# Reads: server/data-talk-application/src/main/resources/dashboard/pattern-catalog.yaml
# Writes:
#   server/data-talk-adapter/src/main/resources/skills/bezel/references/patterns-catalog.md
#   server/data-talk-adapter/src/main/resources/skills/bezel/references/layout-templates.md
#
# CI gate: run this script, then `git diff --exit-code` to detect drift.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CATALOG_YAML="$REPO_ROOT/server/data-talk-application/src/main/resources/dashboard/pattern-catalog.yaml"
OUTPUT_DIR="$REPO_ROOT/server/data-talk-adapter/src/main/resources/skills/bezel/references"

if [ ! -f "$CATALOG_YAML" ]; then
  echo "ERROR: pattern-catalog.yaml not found at $CATALOG_YAML" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

# Use python3 for reliable YAML parsing (available on all CI runners)
python3 - "$CATALOG_YAML" "$OUTPUT_DIR" << 'PYTHON_SCRIPT'
import sys
from pathlib import Path

catalog_path = sys.argv[1]
output_dir = Path(sys.argv[2])

content = Path(catalog_path).read_text()

# ---- Simple YAML parser for our flat structure ----
def parse_catalog(text):
    """Parse pattern-catalog.yaml into structured dicts."""
    result = {
        'chartTypes': [],
        'templates': [],
        'themes': [],
        'patterns': [],
    }

    section = None
    current_item = None

    for line in text.split('\n'):
        stripped = line.strip()

        if line.startswith('chartTypes:'):
            section = 'chartTypes'
            continue
        elif line.startswith('templates:'):
            if current_item and section == 'templates':
                result['templates'].append(current_item)
                current_item = None
            section = 'templates'
            continue
        elif line.startswith('themes:'):
            if current_item and section == 'templates':
                result['templates'].append(current_item)
                current_item = None
            section = 'themes'
            continue
        elif line.startswith('patterns:'):
            if current_item and section == 'templates':
                result['templates'].append(current_item)
                current_item = None
            section = 'patterns'
            continue

        if section == 'chartTypes':
            if line.startswith('  ') and not line.startswith('    ') and ':' in line:
                name = line.strip().split(':')[0].strip()
                result['chartTypes'].append(name)

        elif section == 'templates':
            if line.startswith('  - id:'):
                if current_item:
                    result['templates'].append(current_item)
                current_item = {
                    'id': line.split('id:')[1].strip(),
                    'description': '',
                    'style': '',
                    'slots': [],
                }
            elif current_item:
                if line.strip().startswith('description:'):
                    current_item['description'] = line.split('description:')[1].strip().strip('"')
                elif line.strip().startswith('style:'):
                    current_item['style'] = line.split('style:')[1].strip()
                elif line.strip().startswith('- {'):
                    slot_str = line.strip()[2:].strip()
                    slot = {}
                    for part in slot_str.strip('{').strip('}').split(','):
                        if ': ' in part:
                            k, v = part.strip().split(': ', 1)
                            slot[k.strip()] = v.strip()
                    current_item['slots'].append(slot)
                elif not line.startswith(' ') and stripped and not stripped.startswith('#'):
                    section = None
                    result['templates'].append(current_item)
                    current_item = None

        elif section == 'themes':
            if line.startswith('  - {'):
                entry_str = line.strip()[3:].strip()
                entry = {}
                for part in entry_str.strip('{').strip('}').split(','):
                    if ': ' in part:
                        k, v = part.strip().split(': ', 1)
                        entry[k.strip()] = v.strip()
                result['themes'].append(entry)
            elif not line.startswith(' ') and stripped and not stripped.startswith('#'):
                section = None

        elif section == 'patterns':
            if line.startswith('  ') and not line.startswith('    ') and ':' in line and not stripped.startswith('#'):
                if current_item:
                    result['patterns'].append(current_item)
                name = line.strip().split(':')[0].strip()
                current_item = {
                    'id': name,
                    'description': '',
                    'renderKind': '',
                    'defaultChartType': '',
                    'supportedChartTypes': '',
                    'defaultColorScheme': '',
                    'industry': '',
                }
            elif current_item and not stripped.startswith('#'):
                if line.strip().startswith('description:'):
                    current_item['description'] = line.split('description:')[1].strip().strip('"')
                elif line.strip().startswith('renderKind:'):
                    current_item['renderKind'] = line.split('renderKind:')[1].strip()
                elif line.strip().startswith('defaultChartType:'):
                    current_item['defaultChartType'] = line.split('defaultChartType:')[1].strip()
                elif line.strip().startswith('supportedChartTypes:'):
                    current_item['supportedChartTypes'] = line.split('supportedChartTypes:')[1].strip()
                elif line.strip().startswith('defaultColorScheme:'):
                    current_item['defaultColorScheme'] = line.split('defaultColorScheme:')[1].strip()
                elif line.strip().startswith('industry:'):
                    current_item['industry'] = line.split('industry:')[1].strip()
                elif not line.startswith(' ') and stripped:
                    section = None
                    result['patterns'].append(current_item)
                    current_item = None

    if current_item:
        if section == 'templates':
            result['templates'].append(current_item)
        elif section == 'patterns':
            result['patterns'].append(current_item)

    return result

catalog = parse_catalog(content)

# ---- Generate patterns-catalog.md ----
out = output_dir / 'patterns-catalog.md'
with open(out, 'w') as f:
    f.write('<!-- AUTO-GENERATED by scripts/generate-bezel-docs.sh -- DO NOT EDIT MANUALLY -->\n')
    f.write('<!-- Source: server/data-talk-application/src/main/resources/dashboard/pattern-catalog.yaml -->\n\n')
    f.write('# Bezel Patterns Catalog\n\n')
    f.write('> Single source of truth: `pattern-catalog.yaml`. This file is auto-generated.\n')
    f.write('> Run `scripts/generate-bezel-docs.sh` to regenerate after editing the YAML.\n\n')

    f.write('## Chart Types (8)\n\n')
    f.write('| Type | Default Option |\n')
    f.write('|---|---|\n')
    for ct in catalog['chartTypes']:
        f.write(f'| `{ct}` | `echarts-options/{ct}.json` |\n')
    f.write('\n')

    f.write('## Layout Templates (6)\n\n')
    f.write('See [layout-templates.md](layout-templates.md) for full slot details.\n\n')

    f.write('## Industry-to-Style Mapping\n\n')
    f.write('| Industry Slug | Style | CSS File |\n')
    f.write('|---|---|---|\n')
    for t in catalog['themes']:
        slug = t.get('slug', '?')
        style = t.get('style', '?')
        css = t.get('css', '?')
        f.write(f'| `{slug}` | {style} | `{css}` |\n')
    f.write('\n')

    f.write('## Widget Patterns\n\n')
    f.write('| Pattern ID | Description | renderKind | Default Chart | Supported Charts | Default Color |\n')
    f.write('|---|---|---|---|---|---|\n')
    for p in catalog['patterns']:
        # Skip entries without a dot — template IDs leaked into patterns section
        if '.' not in p.get('id', ''):
            continue
        f.write(f'| `{p.get("id", "")}` | {p.get("description", "")} | `{p.get("renderKind", "")}` | {p.get("defaultChartType", "")} | {p.get("supportedChartTypes", "")} | {p.get("defaultColorScheme", "")} |\n')
    f.write('\n')

    f.write('## Pattern Selection Rules\n\n')
    f.write('1. Business noun weight > industry noun weight\n')
    f.write('2. Table/column name signal > natural language signal\n')
    f.write('3. When multiple industries match, prefer multi-screen\n')
    f.write('4. When unclear, ask the user with 3 candidates\n')

print(f'Generated: {out}')

# ---- Generate layout-templates.md ----
out = output_dir / 'layout-templates.md'
with open(out, 'w') as f:
    f.write('<!-- AUTO-GENERATED by scripts/generate-bezel-docs.sh -- DO NOT EDIT MANUALLY -->\n')
    f.write('<!-- Source: server/data-talk-application/src/main/resources/dashboard/pattern-catalog.yaml -->\n\n')
    f.write('# Bezel Layout Templates\n\n')
    f.write('> Auto-generated from `pattern-catalog.yaml`. Do not edit manually.\n\n')

    f.write('## Template Reference\n\n')
    for tmpl in catalog['templates']:
        f.write(f'### `{tmpl["id"]}`\n\n')
        f.write(f'- **Description**: {tmpl["description"]}\n')
        f.write(f'- **Style**: {tmpl["style"]}\n')
        f.write(f'- **Slots**: {len(tmpl["slots"])} named slot(s)\n\n')

    f.write('## Slot Details\n\n')
    for tmpl in catalog['templates']:
        f.write(f'### {tmpl["id"]}\n\n')
        f.write(f'{tmpl["description"]} (style: {tmpl["style"]})\n\n')
        f.write('| Slot ID | Kind | Capacity |\n')
        f.write('|---|---|---|\n')
        for s in tmpl['slots']:
            f.write(f'| `{s.get("id", "?")}` | `{s.get("kind", "?")}` | {s.get("capacity", "?")} |\n')
        f.write('\n')

    f.write('## Usage\n\n')
    f.write('In v3 JSON, set `layout.template` to one of the template IDs above.\n')
    f.write('Each widget must declare a `slot` matching one of the template\'s named slots.\n')
    f.write('The compiler validates that widgets fit within slot capacities.\n')

print(f'Generated: {out}')
PYTHON_SCRIPT

echo ""
echo "All docs generated successfully."
echo "Run 'git diff --exit-code' to verify no unexpected drift."
