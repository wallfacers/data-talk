# Style Matrix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single-template design system with a 6-style matrix so that each of the 12 industry dashboards has a visually distinct layout, card style, KPI form, chart configuration, background, and motion language.

**Architecture:** Decouple industry (data semantics) from visual style. Create a `references/styles/` directory with 6 style spec files. Each industry file is slimmed to data-only. The compile-rules.md pipeline gains a style resolution step. Token cascade: base → style → industry.

**Tech Stack:** Markdown spec files, CSS custom properties, ECharts 5.5.0, pure HTML (no frameworks), Python validate.py

**Spec:** `docs/superpowers/specs/2026-05-12-style-matrix-design.md`

---

## File Map

### New files
| File | Responsibility |
|------|---------------|
| `skills/bezel/references/styles/orbital.md` | Orbital style spec (~250 lines) |
| `skills/bezel/references/styles/mosaic.md` | Mosaic style spec (~250 lines) |
| `skills/bezel/references/styles/horizon.md` | Horizon style spec (~250 lines) |
| `skills/bezel/references/styles/organic.md` | Organic style spec (~250 lines) |
| `skills/bezel/references/styles/monument.md` | Monument style spec (~250 lines) |
| `skills/bezel/references/styles/terrain.md` | Terrain style spec (~250 lines) |

### Modified files
| File | Change |
|------|--------|
| `skills/bezel/references/design-language.md` | Rewrite: add style matrix layer, keep base tokens |
| `skills/bezel/references/patterns-catalog.md` | Rewrite: add mapping table + style selection guide |
| `skills/bezel/references/compile-rules.md` | Update STEP 2: add style resolution |
| `skills/bezel/SKILL.md` | Update read order |
| `skills/bezel/scripts/validate.py` | Add CSS token completeness check |
| `skills/bezel/references/industries/01-multi-screen.md` | Slim: remove visual sections |
| `skills/bezel/references/industries/02-ecommerce.md` | Slim: remove visual sections |
| `skills/bezel/references/industries/03-manufacturing.md` | Slim: remove visual sections |
| `skills/bezel/references/industries/04-saas.md` | Slim: remove visual sections |
| `skills/bezel/references/industries/05-finance.md` | Slim: remove visual sections |
| `skills/bezel/references/industries/06-logistics.md` | Slim: remove visual sections |
| `skills/bezel/references/industries/07-healthcare.md` | Slim: remove visual sections |
| `skills/bezel/references/industries/08-hr.md` | Slim: remove visual sections |
| `skills/bezel/references/industries/09-energy.md` | Slim: remove visual sections |
| `skills/bezel/references/industries/10-cybersecurity.md` | Slim: remove visual sections |
| `skills/bezel/references/industries/11-agriculture.md` | Slim: remove visual sections |
| `skills/bezel/references/industries/12-education.md` | Slim: remove visual sections |

### Unchanged files
- `skills/bezel/references/data-contract.md`
- `skills/bezel/scripts/preview.py`
- `skills/bezel/assets/templates/*.html` (templates are rewritten in Phase 4 but the old ones serve as visual reference)

---

## Phase 1: Infrastructure

### Task 1: Create styles/ directory and orbital.md skeleton

**Files:**
- Create: `skills/bezel/references/styles/orbital.md`

- [ ] **Step 1: Create directory**

```bash
mkdir -p skills/bezel/references/styles
```

- [ ] **Step 2: Write orbital.md**

Write the first complete style file. This establishes the template that all 6 style files follow. Content follows the spec Section 1.1 (Orbital personality) and Section 3 (token values).

```markdown
# Orbital Style Specification

> Visual personality: Everything revolves around a center. Circles, arcs, and rings express hierarchy and flow.
> Mapped industries: 01-multi-screen, 10-cybersecurity

---

## 1. Layout Skeleton

```
┌─────────────────────────────────────────┐
│            header (narrow, centered)     │
├─────────────────────────────────────────┤
│                                         │
│     ┌──────┐     ┌──────┐              │
│   ┌─┤widget├─┐ ┌─┤KPI  ├─┐            │
│   │ └──────┘ │ │ └──────┘ │            │
│   │  ┌────────────────────┐            │
│   └──┤   CORE WIDGET     ├─┘           │
│      └────────────────────┘            │
│   ┌──────┐              ┌──────┐       │
│   │widget│              │widget│       │
│   └──────┘              └──────┘       │
└─────────────────────────────────────────┘
```

### CSS Implementation

The outer container uses `position: relative`. All widgets are `position: absolute` with `top`/`left` percentages specified by the industry file. The core widget is centered via:

```css
.dashboard {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
.widget-core {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 36%;
  height: 42%;
}
```

Surrounding widgets are positioned by the industry file via inline `style="top:X%; left:Y%; width:W%; height:H%"`.

### Header

A narrow fixed bar at top, centered title + clock. 48px height.

```css
.header {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  background: rgba(var(--bg-rgb), 0.60);
  backdrop-filter: blur(8px);
}
.header h1 {
  font-size: 20px;
  font-weight: 400;
  letter-spacing: 4px;
  color: var(--bezel-text-strong);
}
```

---

## 2. Component Tokens

### :root CSS Variables

```css
:root {
  /* Card */
  --card-radius: 24px;
  --card-bg: rgba(var(--bg-rgb), 0.35);
  --card-border: 1px dashed rgba(var(--accent-primary-rgb), 0.30);
  --card-shadow: none;
  --card-padding: 16px;
  --card-blur: 6px;

  /* Motion */
  --motion-duration: 400ms;
  --motion-easing: ease-in-out;
  --motion-ambient: orbit 12s linear infinite;
}

@media (prefers-reduced-motion: reduce) {
  :root {
    --motion-duration: 0ms;
    --motion-ambient: none;
  }
}
```

### Card Component

```css
.card {
  background: var(--card-bg);
  border: var(--card-border);
  border-radius: var(--card-radius);
  backdrop-filter: blur(var(--card-blur));
  padding: var(--card-padding);
  display: flex;
  flex-direction: column;
  position: relative;
  transition: border var(--motion-duration) var(--motion-easing),
              box-shadow var(--motion-duration) var(--motion-easing);
}
.card:hover {
  border-style: solid;
  border-color: rgba(var(--accent-primary-rgb), 0.55);
  box-shadow: 0 0 20px rgba(var(--accent-primary-rgb), 0.15);
}
```

### Card Title

```css
.card-title {
  font-size: 13px;
  font-weight: 400;
  color: var(--bezel-accent-primary);
  text-align: center;
  letter-spacing: 2px;
  margin-bottom: 8px;
}
```

Note: No `::before` left-bar. Orbital titles are centered and lightweight.

### KPI — Ring Indicator

KPIs appear as small ring gauges distributed around the center widget, not in a dedicated row.

```css
.kpi-ring {
  width: 80px;
  height: 80px;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.kpi-ring .value {
  font-size: 18px;
  font-weight: 700;
  color: var(--bezel-text-strong);
  font-variant-numeric: tabular-nums;
}
.kpi-ring .label {
  font-size: 10px;
  color: var(--bezel-text-muted);
  letter-spacing: 1px;
}
```

Each KPI ring is an ECharts gauge instance with `startAngle: 90`, `endAngle: -270`, rendered into a small 80x80 div.

---

## 3. Chart Configuration

### Recommended Chart Types

| Priority | Type | Usage |
|----------|------|-------|
| 1 | radar | Multi-dimension comparison |
| 2 | gauge (270-degree) | KPI ring indicators |
| 3 | pie (donut) | Category distribution |
| 4 | bar (polar) | Quantity comparison |
| 5 | line (polar angle axis) | Cyclic trends |

### ECharts Option Templates

**Gauge (KPI ring):**

```js
{
  type: 'gauge',
  startAngle: 90,
  endAngle: -270,
  min: 0,
  max: 100,
  radius: '90%',
  axisLine: {
    lineStyle: {
      width: 4,
      color: [[0.3, 'rgba(var(--accent-primary-rgb), 0.15)'], [1, 'var(--bezel-accent-primary)']]
    }
  },
  pointer: { show: false },
  axisTick: { show: false },
  splitLine: { show: false },
  axisLabel: { show: false },
  detail: {
    valueAnimation: true,
    fontSize: 14,
    fontWeight: 'bold',
    color: 'var(--bezel-text-strong)',
    offsetCenter: [0, '-5%'],
    formatter: '{value}%'
  },
  title: {
    show: true,
    fontSize: 9,
    color: 'var(--bezel-text-muted)',
    offsetCenter: [0, '25%']
  }
}
```

**Radar:**

```js
{
  type: 'radar',
  radar: {
    indicator: [], // from data
    axisLine: { lineStyle: { color: 'rgba(var(--accent-primary-rgb), 0.20)' } },
    splitLine: { lineStyle: { color: 'rgba(var(--accent-primary-rgb), 0.10)' } },
    splitArea: { show: false }
  },
  series: [{
    type: 'radar',
    areaStyle: { color: 'rgba(var(--accent-primary-rgb), 0.20)' },
    lineStyle: { color: 'var(--bezel-accent-primary)', width: 2 }
  }]
}
```

**Bar (polar coordinate):**

```js
{
  polar: { radius: ['15%', '70%'] },
  angleAxis: { max: 100, startAngle: 90 },
  radiusAxis: { type: 'category', data: [] },
  series: [{
    type: 'bar',
    coordinateSystem: 'polar',
    itemStyle: { color: 'var(--bezel-accent-primary)' }
  }]
}
```

### Color Mapping

Industry palette colors map directly to chart series. First color = primary series, second = secondary. No gradient fills in Orbital — solid colors only, leveraging the circular/radial geometry for visual interest.

---

## 4. Background

### Implementation

Canvas-based: concentric circles as radar grid + SVG orbit arcs with CSS rotation.

**HTML structure:**

```html
<canvas id="bg-radar" style="position:fixed;inset:0;z-index:0;pointer-events:none;"></canvas>
<div class="orbit-arc arc-1"></div>
<div class="orbit-arc arc-2"></div>
<div class="dashboard" style="position:relative;z-index:1;">
  <!-- widgets -->
</div>
```

**Canvas concentric circles:**

```js
(function() {
  var c = document.getElementById('bg-radar');
  var ctx = c.getContext('2d', { alpha: false });
  function draw() {
    c.width = window.innerWidth;
    c.height = window.innerHeight;
    var cx = c.width / 2, cy = c.height / 2;
    var maxR = Math.max(cx, cy) * 1.2;
    ctx.fillStyle = 'var(--bezel-bg-app)';
    ctx.fillRect(0, 0, c.width, c.height);
    for (var r = 60; r < maxR; r += 60) {
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.strokeStyle = 'rgba(var(--accent-primary-rgb), 0.06)';
      ctx.lineWidth = 1;
      ctx.stroke();
    }
  }
  draw();
  window.addEventListener('resize', draw);
})();
```

**Orbit arcs (CSS):**

```css
.orbit-arc {
  position: fixed;
  border-radius: 50%;
  border: 1px solid rgba(var(--accent-primary-rgb), 0.08);
  pointer-events: none;
  z-index: 0;
}
.arc-1 {
  width: 50vw; height: 50vw;
  top: calc(50% - 25vw); left: calc(50% - 25vw);
  animation: orbit 15s linear infinite;
}
.arc-2 {
  width: 70vw; height: 70vw;
  top: calc(50% - 35vw); left: calc(50% - 35vw);
  animation: orbit 22s linear infinite reverse;
}
@keyframes orbit {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
@media (prefers-reduced-motion: reduce) {
  .orbit-arc { animation: none; }
}
```

---

## 5. Motion

### Ambient

Orbit arcs rotate (see Background section). No other ambient animation.

### Hover / Transition

Cards: dashed border → solid border + glow on hover. Duration 400ms, ease-in-out.

### KPI Count-up

```js
// Standard bezel KPI count-up with Orbital duration
function countUp(el, target, duration) {
  duration = duration || 1200;
  var start = 0;
  var startTime = null;
  function tick(ts) {
    if (!startTime) startTime = ts;
    var p = Math.min((ts - startTime) / duration, 1);
    var ease = 1 - Math.pow(1 - p, 3);
    el.textContent = (target * ease).toFixed(el.dataset.decimal || 0) + (el.dataset.suffix || '');
    if (p < 1) requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
}
```

---

## 6. Accessibility Checklist

- [ ] All text meets WCAG AA 4.5:1 against card background (rgba 0.35 opacity). Verify with contrast checker.
- [ ] KPI ring values must have `aria-label` with full metric name and value.
- [ ] `prefers-reduced-motion` disables orbit rotation and hover transitions (see CSS above).
- [ ] No focus-visible concerns — Orbital dashboards are display-only (no interactive controls).

---

## 7. Performance Notes

- Canvas concentric circles: drawn once on load and on resize only. No animation loop needed.
- Orbit arcs: CSS-only rotation, GPU-composited. `will-change: transform` applied.
- Canvas uses `{ alpha: false }` for performance.

---

## 8. Token Self-Check

| Token | Value |
|-------|-------|
| --card-radius | 24px |
| --card-bg opacity | 0.35 |
| --card-border | 1px dashed rgba 0.30 |
| --card-shadow | none |
| --card-padding | 16px |
| --card-blur | 6px |
| --card-hover | dashed→solid + glow |
| --motion-duration | 400ms |
| --motion-easing | ease-in-out |
| --motion-ambient | orbit 12s linear infinite |
```

- [ ] **Step 3: Verify file exists**

```bash
wc -l skills/bezel/references/styles/orbital.md
```

Expected: ~250 lines

- [ ] **Step 4: Commit**

```bash
git add skills/bezel/references/styles/
git commit -m "feat: add styles/ directory with orbital.md style spec"
```

---

### Task 2: Write mosaic.md style spec

**Files:**
- Create: `skills/bezel/references/styles/mosaic.md`

- [ ] **Step 1: Write mosaic.md**

Follow the exact same section structure as orbital.md (sections 1-8), but with Mosaic-specific values from the spec:

Key differences from Orbital:
- Layout: CSS Grid with `grid-template-areas`, no header bar (title in first cell), KPIs as grid cells
- Card: `--card-radius: 4px`, `--card-border: none`, `--card-bg` opacity 0.85, `--card-blur: 0`, hover: content `translateY(-2px)`
- Title: embedded top-left, 11px uppercase, wide letter-spacing
- KPI: Number block — compact number + tiny label, 32px+ font, no border, distinguished by background color
- Charts: bar (square, solid fill, no gradient), heatmap, treemap, sparklines. Step lines. Minimal grid padding (left:32, right:8)
- Background: solid color or very subtle linear gradient, single `background: linear-gradient(...)`
- Motion: `--motion-duration: 150ms`, `--motion-easing: cubic-bezier(0.4,0,0.2,1)`, `--motion-ambient: none`

Layout CSS:

```css
.dashboard {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: auto;
  gap: 6px;
  padding: 6px;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
/* grid-template-areas defined per industry file */
```

Card CSS:

```css
.card {
  background: var(--card-bg);
  border-radius: var(--card-radius);
  padding: var(--card-padding);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  transition: transform var(--motion-duration) var(--motion-easing);
}
.card:hover {
  transform: translateY(-2px);
}
.card-inner {
  transition: transform var(--motion-duration) var(--motion-easing);
}
```

Title CSS:

```css
.card-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--bezel-text-muted);
  text-transform: uppercase;
  letter-spacing: 2px;
  margin-bottom: 6px;
}
```

KPI block CSS:

```css
.kpi-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px;
}
.kpi-block .value {
  font-size: 32px;
  font-weight: 700;
  color: var(--bezel-text-strong);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.kpi-block .label {
  font-size: 10px;
  color: var(--bezel-text-muted);
  margin-top: 4px;
}
```

Background: `background: linear-gradient(135deg, var(--bezel-bg-app) 0%, rgba(var(--accent-primary-rgb), 0.03) 100%);`

Include sections 6 (accessibility), 7 (performance), and 8 (token self-check) matching the orbital.md structure. For Mosaic: high bg opacity means good contrast by default, but verify muted label text meets 4.5:1. No performance concerns — no canvas, no blur.

- [ ] **Step 2: Commit**

```bash
git add skills/bezel/references/styles/mosaic.md
git commit -m "feat: add mosaic.md style spec"
```

---

### Task 3: Write organic.md style spec

**Files:**
- Create: `skills/bezel/references/styles/organic.md`

- [ ] **Step 1: Write organic.md**

Follow the same 8-section structure with Organic values:

Key differences:
- Layout: `flex-wrap` with varied widths and gaps, extra margin on select elements, 20-30% more whitespace
- Card: `--card-radius: 18px`, `--card-bg` opacity 0.40, `--card-blur: 14px`, multi-layer soft shadows
- Title: left-aligned, 14px, soft colors
- KPI: soft card with icon + description, flexible placement
- Charts: smooth curves, liquid fill, pictorialBar. Colors desaturated 15-20%
- Background: soft color blobs (2-3 large `filter:blur(100px)` divs) + faint noise
- Motion: `--motion-duration: 500ms`, `--motion-easing: cubic-bezier(0.25,0.1,0.25,1)`, `--motion-ambient: breathing 6s`

Layout CSS:

```css
.dashboard {
  position: relative;
  z-index: 1;
  width: 100vw;
  height: 100vh;
  padding: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-content: flex-start;
  overflow: hidden;
}
.widget-wide { flex: 2 1 55%; min-width: 300px; }
.widget-narrow { flex: 1 1 30%; min-width: 200px; }
.widget-breathe { margin-top: 12px; } /* extra whitespace on select widgets */
```

Card CSS:

```css
.card {
  background: var(--card-bg);
  border-radius: var(--card-radius);
  border: 1px solid rgba(var(--accent-primary-rgb), 0.12);
  backdrop-filter: blur(var(--card-blur));
  padding: var(--card-padding);
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08), 0 8px 24px rgba(0,0,0,0.06);
  transition: transform var(--motion-duration) var(--motion-easing),
              box-shadow var(--motion-duration) var(--motion-easing);
}
.card:hover {
  transform: scale(1.01);
  box-shadow: 0 2px 8px rgba(0,0,0,0.10), 0 12px 32px rgba(0,0,0,0.10);
}
```

Background HTML + CSS:

```html
<div class="bg-blob blob-1"></div>
<div class="bg-blob blob-2"></div>
<div class="bg-blob blob-3"></div>
```

```css
.bg-blob {
  position: fixed;
  border-radius: 50%;
  filter: blur(100px);
  pointer-events: none;
  z-index: 0;
  will-change: transform;
}
.blob-1 {
  width: 60vw; height: 60vw;
  top: -15vw; left: -10vw;
  background: radial-gradient(circle, rgba(var(--accent-primary-rgb), 0.18), transparent 70%);
  animation: drift1 10s ease-in-out infinite alternate;
}
.blob-2 {
  width: 50vw; height: 50vw;
  bottom: -10vw; right: -10vw;
  background: radial-gradient(circle, rgba(var(--accent-secondary-rgb), 0.15), transparent 70%);
  animation: drift2 12s ease-in-out infinite alternate;
}
@keyframes drift1 { to { transform: translate(5vw, 3vw); } }
@keyframes drift2 { to { transform: translate(-4vw, -5vw); } }
@media (prefers-reduced-motion: reduce) {
  .bg-blob { animation: none; filter: blur(80px); }
}
```

Accessibility: low bg opacity (0.40) + blur requires explicit contrast verification. Specify minimum text colors: `--bezel-text-strong` must be at least `#e2e8f0`, `--bezel-text-muted` at least `#94a3b8` to pass 4.5:1 against the blurred card background.

Performance: `filter:blur(100px)` on large divs. Add `will-change: transform`. Reduce to 80px when `prefers-reduced-motion` is active.

- [ ] **Step 2: Commit**

```bash
git add skills/bezel/references/styles/organic.md
git commit -m "feat: add organic.md style spec"
```

---

### Task 4: Write horizon.md, monument.md, terrain.md style specs

**Files:**
- Create: `skills/bezel/references/styles/horizon.md`
- Create: `skills/bezel/references/styles/monument.md`
- Create: `skills/bezel/references/styles/terrain.md`

- [ ] **Step 1: Write horizon.md**

8-section structure. Key values:
- Layout: 3-4 stacked `<section>` flex containers, band heights via `flex: N`, gradient line separators
- Card: `--card-radius: 8px`, bottom border only, `--card-padding: 12px 20px`
- Title: left-aligned with bottom border accent
- KPI: horizontal bar, label-left value-right, bottom separator
- Charts: horizontal bars, sankey, area charts with horizontal gradient
- Background: CSS gradient + `repeating-linear-gradient` horizontal contour lines
- Motion: `--motion-duration: 250ms`, `--motion-easing: ease-out`, `--motion-ambient: scan 4s`

- [ ] **Step 2: Write monument.md**

8-section structure. Key values:
- Layout: strict symmetric flex, mirror layout, uniform `gap: 16px`
- Card: `--card-radius: 2px`, `border: 2px double`, inner frame structure, `--card-blur: 0`
- Title: serif font via CDN (Noto Serif SC with fallback), uppercase, centered, decorative line below
- KPI: refined frame, double-line border, serif numerals
- Charts: classic bar (no gradient, no radius), precise line, tables
- Background: dark warm + SVG pattern inlined in CSS at very low opacity
- Motion: `--motion-duration: 200ms`, `--motion-easing: linear`, `--motion-ambient: none`

Font CDN addition to HTML skeleton (Monument only):

```html
<link href="https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400;700&display=swap" rel="stylesheet">
```

This requires updating compile-rules.md CSP to allow `fonts.googleapis.com` and `fonts.gstatic.com` in `font-src` for Monument dashboards.

- [ ] **Step 3: Write terrain.md**

8-section structure. Key values:
- Layout: 2-column flex (35%/65%), left stack + right map area
- Card: `--card-radius: 14px`, bottom grounding shadow
- Title: left-aligned with `::before` SVG data URI icon
- KPI: icon + number card, bottom row, SVG data URI natural icons
- Charts: map, heatmap, effectScatter, pictorialBar with natural SVG paths
- Background: bottom-to-top vertical gradient (ground→sky)
- Motion: `--motion-duration: 350ms`, `--motion-easing: ease-in-out`, `--motion-ambient: ripple 5s`

Decorative element spec (Terrain-specific):

```css
.card::after {
  content: '';
  position: absolute;
  top: 8px;
  right: 8px;
  width: 16px;
  height: 16px;
  background: var(--deco-svg) no-repeat center;
  background-size: contain;
  opacity: 0.3;
}
```

Where `--deco-svg` is an inline SVG data URI provided by each industry file (e.g., leaf for Agriculture, lightning bolt for Energy).

- [ ] **Step 4: Commit**

```bash
git add skills/bezel/references/styles/
git commit -m "feat: add horizon, monument, terrain style specs"
```

---

### Task 5: Rewrite design-language.md

**Files:**
- Modify: `skills/bezel/references/design-language.md`

- [ ] **Step 1: Rewrite the file**

Replace the current 780-line file. New structure:

```markdown
# Bezel Design Language v2 — Style Matrix System

## 1. Architecture Overview

Bezel uses a **style matrix** that decouples industry (data semantics) from visual style (layout + components + atmosphere).

Token cascade order (later wins):
1. **Base layer** — this file. Spacing scale, typography, naming conventions, shared ECharts baseline.
2. **Style layer** — `references/styles/<style>.md`. Layout, card shape, KPI form, chart config, background, motion.
3. **Industry layer** — `references/industries/<industry>.md`. Color overrides only (accent-primary, accent-secondary, bg-app).

## 2. Base Token Naming Convention

(retain current Section 1.1 CSS variable naming table unchanged)

## 3. Spacing Scale

(retain current Section 3 unchanged — sp-1 through sp-8)

## 4. Typography

(retain current Section 2 font stack and type scale, minus the card-title ::before pattern which is now style-specific)

## 5. Default Neutral Values

(retain current Section 1.2 as the fallback palette when no industry override is specified)

## 6. Per-Industry Color Override Table

(retain current Section 1.3 table — 12 industry rows with accent and bg values. Add a "Style" column.)

| # | Industry | Style | --bezel-bg-app | --bezel-accent-primary | --bezel-accent-secondary |
|---|----------|-------|----------------|------------------------|--------------------------|
| 01 | Multi-Screen | Orbital | #060b14 | #00d4ff | #10e873 |
| 02 | E-Commerce | Mosaic | #0d0a07 | #ff4444 | #ffc107 |
| 03 | Manufacturing | Horizon | #1a1a1e | #f97316 | #facc15 |
| 04 | SaaS | Mosaic | #0a0f1a | #14b8a6 | #6366f1 |
| 05 | Finance | Monument | #0c1929 | #c9a84c | #4a90d9 |
| 06 | Logistics | Horizon | #0a0e1a | #0ea5e9 | #38bdf8 |
| 07 | Healthcare | Organic | #0f172a | #10b981 | #3b82f6 |
| 08 | HR | Organic | #1f2937 | #f97316 | #3b82f6 |
| 09 | Energy | Terrain | #0a1f14 | #22c55e | #0ea5e9 |
| 10 | Cybersecurity | Orbital | #000a00 | #00ff41 | #ff0040 |
| 11 | Agriculture | Terrain | #1a1209 | #f59e0b | #22c55e |
| 12 | Education | Organic | #0f172a | #facc15 | #3b82f6 |

## 7. ECharts Theme Baseline

(retain current Section 8.1 common variables — backgroundColor, textStyle, title, legend, tooltip, categoryAxis, valueAxis, grid)

Remove Sections 8.4-8.9 (industry palettes, gradient bar, area fill, heatmap, gauge, pie configs). These are now style-specific and live in the style files.

## 8. Glass Morphism

(retain current Section 7 but mark as "default, may be overridden by style layer". Remove specific blur/opacity values since those are now in style files.)

## 9. Accessibility

- All text must meet WCAG AA (4.5:1 body, 3:1 large) against card background
- `prefers-reduced-motion` must suppress all animations in every style
- Font fallback chains must include system fonts

## 10. Token Application Mechanism

Compiled HTML embeds CSS in this order:
1. `:root` with base tokens (spacing, typography)
2. `:root` with style tokens (card, motion, layout from style file)
3. `:root` with industry color overrides (accent colors, bg from industry file)

Later `:root` declarations override earlier ones via CSS cascade.
```

The rewrite keeps ~60% of existing content (base tokens, naming conventions, color table, ECharts baseline) and restructures around the style matrix concept. Remove: Sections 4 (radius — now style-specific), 5 (motion — now style-specific), 6 (background effects — now style-specific), 8.4-8.9 (chart configs — now style-specific).

- [ ] **Step 2: Verify file structure**

```bash
grep "^## " skills/bezel/references/design-language.md
```

Expected output should show the new section headings including "Architecture Overview", "Token Application Mechanism", and "Per-Industry Color Override Table" with the Style column.

- [ ] **Step 3: Commit**

```bash
git add skills/bezel/references/design-language.md
git commit -m "feat: rewrite design-language.md with style matrix architecture"
```

---

### Task 6: Rewrite patterns-catalog.md

**Files:**
- Modify: `skills/bezel/references/patterns-catalog.md`

- [ ] **Step 1: Rewrite the file**

New structure:

```markdown
# Bezel Patterns Catalog v2

## Read Order

1. **This file** → determine industry and mapped style
2. **`references/styles/<style>.md`** → complete visual specification
3. **`references/industries/<industry>.md`** → data semantics and color overrides
4. **`references/compile-rules.md`** → JSON → HTML assembly
5. **`references/data-contract.md`** → JSON schema and polling protocol

## Industry-to-Style Mapping

| # | Industry ID | Chinese Name | Style | Style File |
|---|-------------|-------------|-------|-----------|
| 01 | multi-screen | 多屏综合监控 | Orbital | styles/orbital.md |
| 02 | ecommerce | 电商运营实时监控中心 | Mosaic | styles/mosaic.md |
| 03 | manufacturing | 工业制造智能监控中心 | Horizon | styles/horizon.md |
| 04 | saas | SaaS 运营监控中心 | Mosaic | styles/mosaic.md |
| 05 | finance | 财务数据分析中心 | Monument | styles/monument.md |
| 06 | logistics | 物流供应链监控中心 | Horizon | styles/horizon.md |
| 07 | healthcare | 医疗健康大数据中心 | Organic | styles/organic.md |
| 08 | hr | 人力资源分析中心 | Organic | styles/organic.md |
| 09 | energy | 能源环保监控中心 | Terrain | styles/terrain.md |
| 10 | cybersecurity | 网络安全态势感知中心 | Orbital | styles/orbital.md |
| 11 | agriculture | 智慧农业大数据中心 | Terrain | styles/terrain.md |
| 12 | education | 在线教育数据中心 | Organic | styles/organic.md |

## Style Selection Guide (Custom Industries)

When a user requests an industry not in the 12 presets, use this decision tree:

1. Is the data about **monitoring, surveillance, or 360-degree awareness**? → **Orbital**
2. Is the data **KPI-driven, dense metrics, ops cockpit**? → **Mosaic**
3. Is the data a **sequential process, pipeline, or flow**? → **Horizon**
4. Is the data about **people, care, learning, or HR**? → **Organic**
5. Is the data about **money, regulation, compliance, or authority**? → **Monument**
6. Is the data about **nature, geography, environment, or spatial**? → **Terrain**
7. Still unclear? Default to **Mosaic** (most versatile for general metrics).

## Pattern Selection Priority Rules

(retain current rules 1-4 unchanged)

## Cross-Industry Generic Widget Pattern Library

(retain current generic.* patterns unchanged)

## Template File Locations

(retain current file listing unchanged)
```

- [ ] **Step 2: Commit**

```bash
git add skills/bezel/references/patterns-catalog.md
git commit -m "feat: rewrite patterns-catalog.md with style mapping table and selection guide"
```

---

### Task 7: Update compile-rules.md

**Files:**
- Modify: `skills/bezel/references/compile-rules.md`

- [ ] **Step 1: Update STEP 2 in the assembly algorithm**

Find the STEP 2 section (around line 256-265) and replace with:

```
STEP 2 — Load Industry Reference + Style Reference
  Read references/industries/<dashboard.json.industry>.md.
  Extract:
    - Color overrides (--bezel-accent-primary, --bezel-accent-secondary, --bezel-bg-app)
    - KPI list and widget recommendations
    - AI trigger keywords
  Resolve style name from patterns-catalog.md mapping table.
  Read references/styles/<style>.md.
  Extract:
    - Layout skeleton CSS
    - Card, KPI, title component CSS
    - Chart configuration templates
    - Background implementation HTML+CSS
    - Motion tokens
  If the industry file does not exist, FAIL with error:
    "Industry reference not found: <industry>"
  If the style file does not exist, FAIL with error:
    "Style reference not found: <style>"
```

- [ ] **Step 2: Update STEP 3 CSS assembly note**

In STEP 3 (around line 273-278), update the `<style>` block description to note the three-layer cascade:

```
   g. <style> block:
       - Layer 1: Base tokens from design-language.md (spacing, typography)
       - Layer 2: Style tokens from style file (card, motion, layout)
       - Layer 3: Industry color overrides (accent colors, bg)
       - Component CSS from style file (card, KPI, title, background)
       (Layers are sequential :root blocks — later declarations win via cascade)
```

- [ ] **Step 3: Update Token Replacement Map table**

Add new token row:

```
| `__STYLE_CSS__` | `references/styles/<style>.md` → full CSS template | Orbital/Mosaic/... CSS |
```

- [ ] **Step 4: Commit**

```bash
git add skills/bezel/references/compile-rules.md
git commit -m "feat: update compile-rules.md with style resolution step"
```

---

### Task 8: Update SKILL.md read order

**Files:**
- Modify: `skills/bezel/SKILL.md`

- [ ] **Step 1: Update Reference layout section**

Replace the current "Reference layout" section with:

```markdown
## Reference layout

Read in this order:
1. `references/patterns-catalog.md` → look up industry + mapped style
2. `references/styles/<style>.md` → complete visual spec (layout, cards, charts, background, motion)
3. `references/industries/<industry>.md` → data semantics, KPI list, color overrides, widget recommendations
4. `references/design-language.md` → base tokens (spacing, typography, naming)
5. `references/compile-rules.md` → JSON → HTML assembly algorithm
6. `references/data-contract.md` → JSON schema v2 + polling protocol + `window.__BEZEL_CONFIG__` contract
7. `assets/templates/NN-name.html` → original visual references (read-only inspiration)
8. `scripts/validate.py` → compile-output self-check; run before promoting
9. `scripts/preview.py` → local JSON + mock-data → HTML preview for debugging
```

- [ ] **Step 2: Commit**

```bash
git add skills/bezel/SKILL.md
git commit -m "feat: update SKILL.md read order for style matrix"
```

---

## Phase 2: Industry File Slim-Down

### Task 9: Slim down all 12 industry files

**Files:**
- Modify: `skills/bezel/references/industries/01-multi-screen.md`
- Modify: `skills/bezel/references/industries/02-ecommerce.md`
- ... (all 12 files)

For each industry file, the transformation is:

1. **Keep unchanged:** `## 业务上下文` section, `## 典型 widget pattern` section, `## 触发线索` section
2. **Replace:** `## 视觉签名` → `## 颜色覆盖` (only the 3 color values + Style mapping)
3. **Remove:** `## 推荐布局骨架` section entirely (now in style file)

- [ ] **Step 1: Slim 01-multi-screen.md**

Replace the visual signature section with:

```markdown
## 风格与颜色覆盖

- **映射风格:** Orbital (`references/styles/orbital.md`)
- `--bezel-bg-app`: `#060b14`
- `--bezel-accent-primary`: `#00d4ff` (cyan)
- `--bezel-accent-secondary`: `#10e873` (mint)
```

Remove the layout skeleton section.

- [ ] **Step 2: Slim 02-ecommerce.md**

Replace visual signature with:

```markdown
## 风格与颜色覆盖

- **映射风格:** Mosaic (`references/styles/mosaic.md`)
- `--bezel-bg-app`: `#0d0a07`
- `--bezel-accent-primary`: `#ff4444` (red)
- `--bezel-accent-secondary`: `#ffc107` (gold)
```

Remove layout skeleton section.

- [ ] **Step 3: Repeat for all 12 files**

Apply the same pattern using the mapping from spec Section 2:

| # | Industry | Style | bg-app | accent-primary | accent-secondary |
|---|----------|-------|--------|----------------|------------------|
| 03 | manufacturing | Horizon | #1a1a1e | #f97316 | #facc15 |
| 04 | saas | Mosaic | #0a0f1a | #14b8a6 | #6366f1 |
| 05 | finance | Monument | #0c1929 | #c9a84c | #4a90d9 |
| 06 | logistics | Horizon | #0a0e1a | #0ea5e9 | #38bdf8 |
| 07 | healthcare | Organic | #0f172a | #10b981 | #3b82f6 |
| 08 | hr | Organic | #1f2937 | #f97316 | #3b82f6 |
| 09 | energy | Terrain | #0a1f14 | #22c55e | #0ea5e9 |
| 10 | cybersecurity | Orbital | #000a00 | #00ff41 | #ff0040 |
| 11 | agriculture | Terrain | #1a1209 | #f59e0b | #22c55e |
| 12 | education | Organic | #0f172a | #facc15 | #3b82f6 |

- [ ] **Step 4: Commit**

```bash
git add skills/bezel/references/industries/
git commit -m "feat: slim all 12 industry files to data-only, add style mapping"
```

---

## Phase 3: Validation Update

### Task 10: Extend validate.py with CSS token completeness check

**Files:**
- Modify: `skills/bezel/scripts/validate.py`

- [ ] **Step 1: Add token completeness checker**

Add a new function after the existing `check_fetch_url_shape` function:

```python
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
```

- [ ] **Step 2: Wire into validate function**

In the `validate` function, add after `failures.extend(check_fetch_url_shape(html))`:

```python
    failures.extend(check_css_tokens(html))
```

- [ ] **Step 3: Run existing validate.py on a current template to verify no regressions**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/02-ecommerce.html
```

Expected: PASS (existing templates already have `:root` with these tokens defined, since the current single-style uses the same variable names)

- [ ] **Step 4: Commit**

```bash
git add skills/bezel/scripts/validate.py
git commit -m "feat: add CSS token completeness check to validate.py"
```

---

## Phase 4: HTML Template Rewrite

### Task 11: Rewrite Orbital templates (01-multi-screen, 10-cybersecurity)

**Files:**
- Modify: `skills/bezel/assets/templates/01-multi-screen-dashboard.html`
- Modify: `skills/bezel/assets/templates/10-cybersecurity.html`

- [ ] **Step 1: Rewrite 01-multi-screen-dashboard.html**

Using the Orbital style spec from `references/styles/orbital.md`:
- Replace the 3-column flex layout with center-radiating absolute positioning
- Replace glass cards with Orbital capsule cards (radius 24px, dashed border)
- Replace KPI pills with ring gauges distributed around center
- Replace background with Canvas concentric circles + orbit arcs
- Replace card titles (no `::before` bar, centered lightweight text)
- Keep ECharts charts but reconfigure: replace bar charts with polar bar, add radar charts
- Keep `window.__BEZEL_CONFIG__` and polling scheduler IIFE unchanged
- Ensure CSP meta tag, JSON hash meta, ECharts CDN are preserved

- [ ] **Step 2: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/01-multi-screen-dashboard.html
```

Expected: PASS

- [ ] **Step 3: Rewrite 10-cybersecurity.html**

Same Orbital style, but with cybersecurity-specific data and cybersecurity color overrides (#000a00 bg, #00ff41 accent-primary, #ff0040 accent-secondary).

- [ ] **Step 4: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/10-cybersecurity.html
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add skills/bezel/assets/templates/01-multi-screen-dashboard.html skills/bezel/assets/templates/10-cybersecurity.html
git commit -m "feat: rewrite multi-screen and cybersecurity templates with Orbital style"
```

---

### Task 12: Rewrite Mosaic templates (02-ecommerce, 04-saas)

**Files:**
- Modify: `skills/bezel/assets/templates/02-ecommerce.html`
- Modify: `skills/bezel/assets/templates/04-saas.html`

- [ ] **Step 1: Rewrite 02-ecommerce.html**

Using Mosaic style spec:
- Replace layout with CSS Grid `grid-template-areas` (no header bar, title in first cell)
- Replace glass cards with borderless blocks (radius 4px, high opacity 0.85 bg)
- Replace KPI pills with compact number blocks (32px+ font, embedded in grid)
- Replace particle background with solid subtle gradient
- Replace card titles with embedded 11px uppercase style
- Charts: solid fill bars (no gradient), sparklines, heatmap — no area fills or smooth curves
- Keep `window.__BEZEL_CONFIG__` and polling scheduler unchanged

- [ ] **Step 2: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/02-ecommerce.html
```

Expected: PASS

- [ ] **Step 3: Rewrite 04-saas.html**

Same Mosaic style, SaaS-specific data and colors (#0a0f1a bg, #14b8a6 primary, #6366f1 secondary).

- [ ] **Step 4: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/04-saas.html
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add skills/bezel/assets/templates/02-ecommerce.html skills/bezel/assets/templates/04-saas.html
git commit -m "feat: rewrite ecommerce and saas templates with Mosaic style"
```

---

### Task 13: Rewrite Horizon templates (03-manufacturing, 06-logistics)

**Files:**
- Modify: `skills/bezel/assets/templates/03-manufacturing.html`
- Modify: `skills/bezel/assets/templates/06-logistics.html`

- [ ] **Step 1: Rewrite 03-manufacturing.html**

Using Horizon style spec:
- Replace layout with 3-4 horizontal bands (strategy → execution → detail)
- Replace cards with wide flat rectangles (bottom border only)
- Replace KPI pills with horizontal bar indicators (label-left value-right)
- Replace background with top-to-bottom gradient + contour lines
- Charts: horizontal bars, sankey, area with horizontal gradient, left-to-right funnels
- Keep polling scheduler unchanged

- [ ] **Step 2: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/03-manufacturing.html
```

- [ ] **Step 3: Rewrite 06-logistics.html**

Same Horizon style, logistics-specific data and colors (#0a0e1a bg, #0ea5e9 primary, #38bdf8 secondary).

- [ ] **Step 4: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/06-logistics.html
```

- [ ] **Step 5: Commit**

```bash
git add skills/bezel/assets/templates/03-manufacturing.html skills/bezel/assets/templates/06-logistics.html
git commit -m "feat: rewrite manufacturing and logistics templates with Horizon style"
```

---

### Task 14: Rewrite Organic templates (07-healthcare, 08-hr, 12-education)

**Files:**
- Modify: `skills/bezel/assets/templates/07-healthcare.html`
- Modify: `skills/bezel/assets/templates/08-hr.html`
- Modify: `skills/bezel/assets/templates/12-education.html`

- [ ] **Step 1: Rewrite 07-healthcare.html**

Using Organic style spec:
- Replace layout with flex-wrap irregular flow (varied widths, extra whitespace)
- Replace cards with large-radius soft cards (18px, multi-layer shadow, blur 14px)
- Replace KPI row with soft cards with icons and descriptions, flexible placement
- Replace background with blurred color blobs
- Charts: smooth curves, soft area fills, colors desaturated 15-20%
- Keep polling scheduler unchanged

- [ ] **Step 2: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/07-healthcare.html
```

- [ ] **Step 3: Rewrite 08-hr.html**

Same Organic style, HR-specific data and colors (#1f2937 bg, #f97316 primary, #3b82f6 secondary).

- [ ] **Step 4: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/08-hr.html
```

- [ ] **Step 5: Rewrite 12-education.html**

Same Organic style, education-specific data and colors (#0f172a bg, #facc15 primary, #3b82f6 secondary).

- [ ] **Step 6: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/12-education.html
```

- [ ] **Step 7: Commit**

```bash
git add skills/bezel/assets/templates/07-healthcare.html skills/bezel/assets/templates/08-hr.html skills/bezel/assets/templates/12-education.html
git commit -m "feat: rewrite healthcare, hr, education templates with Organic style"
```

---

### Task 15: Rewrite Monument template (05-finance)

**Files:**
- Modify: `skills/bezel/assets/templates/05-finance.html`

- [ ] **Step 1: Rewrite 05-finance.html**

Using Monument style spec:
- Replace layout with strict symmetric flex (mirror left-right, uniform gap 16px)
- Replace cards with fine-bordered cards (2px double border, inner frame, radius 2px)
- Replace KPI pills with refined frames (double-line border, serif numerals, centered)
- Add Noto Serif SC font via `<link>` CDN tag
- Replace background with dark warm + faint diamond/lattice SVG texture
- Charts: classic bar (no gradient, no radius), precise line (no area fill), tables
- No decorative animations — only number updates
- Update CSP `font-src` to include `https://fonts.googleapis.com https://fonts.gstatic.com`

- [ ] **Step 2: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/05-finance.html
```

Expected: PASS (note: the font-src CSP update may need to be reflected in compile-rules.md CSP template)

- [ ] **Step 3: Commit**

```bash
git add skills/bezel/assets/templates/05-finance.html
git commit -m "feat: rewrite finance template with Monument style"
```

---

### Task 16: Rewrite Terrain templates (09-energy, 11-agriculture)

**Files:**
- Modify: `skills/bezel/assets/templates/09-energy.html`
- Modify: `skills/bezel/assets/templates/11-agriculture.html`

- [ ] **Step 1: Rewrite 09-energy.html**

Using Terrain style spec:
- Replace layout with 2-column flex (35%/65%), left stack + right map area
- Replace cards with medium-radius cards (14px) with bottom grounding shadow
- Add `::after` decorative element (lightning bolt SVG data URI for Energy)
- Replace KPI row with bottom-row icon+number cards
- Replace background with ground-to-sky vertical gradient + optional wave at bottom
- Charts: map, heatmap, effectScatter (ripple), pictorialBar with natural elements

- [ ] **Step 2: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/09-energy.html
```

- [ ] **Step 3: Rewrite 11-agriculture.html**

Same Terrain style, agriculture-specific data and colors (#1a1209 bg, #f59e0b primary, #22c55e secondary). Decorative element: leaf SVG data URI.

- [ ] **Step 4: Run validate.py**

```bash
python3 skills/bezel/scripts/validate.py skills/bezel/assets/templates/11-agriculture.html
```

- [ ] **Step 5: Commit**

```bash
git add skills/bezel/assets/templates/09-energy.html skills/bezel/assets/templates/11-agriculture.html
git commit -m "feat: rewrite energy and agriculture templates with Terrain style"
```

---

## Phase 5: Final Validation

### Task 17: Run validate.py on all 12 templates

**Files:** (no changes, validation only)

- [ ] **Step 1: Run validate.py on every template**

```bash
for f in skills/bezel/assets/templates/*.html; do
  echo "=== $f ==="
  python3 skills/bezel/scripts/validate.py "$f"
  echo ""
done
```

Expected: all 12 PASS

- [ ] **Step 2: Fix any failures**

If any template fails, read the error message, identify which token or CSP rule is missing, and fix the template.

- [ ] **Step 3: Visual verification**

Open each template in a browser (or use `scripts/preview.py`) and verify:
- The 6 visual styles are visibly distinct from each other
- Within each style, the 2-3 industries look different (via color) but share the same layout DNA
- No template looks like the old "glass morphism + 3-column" pattern

---

## Self-Review

**Spec coverage check:**

| Spec Section | Task |
|-------------|------|
| 1.1-1.6 Visual Personalities | Tasks 1-4 (style files) |
| 2 Industry-Style Mapping | Task 6 (patterns-catalog.md) |
| 3.1 Layout Grammar | Tasks 1-4 (style files) + Tasks 11-16 (templates) |
| 3.2 Card Shape Tokens | Tasks 1-4 (style files) + Tasks 11-16 (templates) |
| 3.3 KPI Form Tokens | Tasks 1-4 (style files) + Tasks 11-16 (templates) |
| 3.4 Chart Config | Tasks 1-4 (style files) + Tasks 11-16 (templates) |
| 3.5 Background Atmosphere | Tasks 1-4 (style files) + Tasks 11-16 (templates) |
| 3.6 Motion Tokens | Tasks 1-4 (style files) |
| 4.1 styles/ directory | Tasks 1-4 |
| 4.2 design-language.md | Task 5 |
| 4.2 patterns-catalog.md | Task 6 |
| 4.2 compile-rules.md | Task 7 |
| 4.2 SKILL.md | Task 8 |
| 4.2 industries/*.md | Task 9 |
| 5 Edge Cases (style override) | Task 7 (compile-rules STEP 2) |
| 6.2 Accessibility | Tasks 1-4 (style files include a11y checklist) |
| 6.3 Performance | Tasks 1-4 (style files include perf notes) |
| 6.4 Token Consistency | Task 10 (validate.py extension) |
| 7 Phase 4 visual regression | Task 17 (visual verification) |

**Placeholder scan:** No TBD/TODO found. All tasks contain specific values or code.

**Type consistency:** CSS variable names (`--card-radius`, `--card-bg`, etc.) are consistent across all tasks. The mapping table values match the spec exactly.
