# bezel

A Claude Code / OpenCode skill that produces premium industrial dashboards rendered from JSON. Turns flat JSON descriptions into self-contained, polling-aware HTML you embed as an iframe.

## What It Does

When Claude is asked to build a dashboard, monitoring screen, KPI board, or operations cockpit, bezel produces:

1. **dashboard.json** — declarative description (theme, layout, widgets with SQL queries and refresh policy)
2. **dashboard.html** — a single, self-contained `.html` file that loads ECharts, parses an embedded `__BEZEL_CONFIG__`, and runs its own polling scheduler against your data API

**12 built-in industry patterns** cover the most common operational dashboards:

| # | Industry | Visual signature |
|---|----------|------------------|
| 01 | Multi-screen control center | Floating KPI capsules, left/right columns |
| 02 | Ecommerce ops | Lava red/gold, funnel, marquee leaderboards, heatmap |
| 03 | Manufacturing | Steel blue, progress rings, SCADA-style cards |
| 04 | SaaS ops | Violet, retention matrix, MRR trends |
| 05 | Finance | Ink green/black/gold, candlesticks, sankey |
| 06 | Logistics | Cyan, route maps, SLA rings |
| 07 | Healthcare | White-blue, ward heatmap, patient profile |
| 08 | HR | Warm gray, org tree, hiring funnel |
| 09 | Energy | Orange-black, grid topology, load curves |
| 10 | Cybersecurity | Dark green/black, alert waterfall, IP arcs |
| 11 | Agriculture | Sage-brown, field maps, phenology curves |
| 12 | Education | Orange-teal, learner radar, progress arrays |

## Installation

### Via Claude Code Plugin Marketplace

```bash
/plugin marketplace add wallfacers/bezel
/plugin install bezel@bezel
```

### Via OpenCode Project-Local Skills

OpenCode loads skills from `<project>/.opencode/skills/<name>/`. The hosting project (e.g., DataTalk) typically vendors bezel's `skills/bezel/` directory into its server's classpath and extracts it on startup. Manual install:

```bash
cp -r skills/bezel ~/your-project/.opencode/skills/bezel
```

## Usage

Once installed, bezel activates automatically when Claude detects dashboard-shaped intent. You can also invoke explicitly:

```
/bezel Build me an ecommerce ops dashboard for mysql-prod
/bezel Healthcare bed-map dashboard
/bezel Manufacturing line monitoring, refresh every 5s
```

## Philosophy

- **JSON is source-of-truth, HTML is derived.** Edit JSON; let bezel re-emit HTML.
- **Self-contained artifacts.** Once produced, the HTML needs nothing but ECharts CDN to run.
- **Refresh as a property, not a feature.** Every widget declares `refresh.intervalMs`; the HTML carries its own scheduler.
- **Industry as design DNA.** A finance dashboard should look nothing like a healthcare dashboard. bezel ships 12 industry patterns with distinct visual signatures.

## Project Structure

```
bezel/
├── .claude-plugin/marketplace.json
├── skills/bezel/
│   ├── SKILL.md
│   ├── references/
│   │   ├── design-language.md
│   │   ├── data-contract.md
│   │   ├── compile-rules.md
│   │   ├── patterns-catalog.md
│   │   └── industries/01-…12-…
│   ├── assets/templates/  (12 original HTML inspirations)
│   └── scripts/validate.py, preview.py
├── README.md
└── LICENSE  (Apache 2.0)
```

## License

Apache 2.0 — see [LICENSE](LICENSE).
