# Monument Style Specification

> Visual personality: Data is solemn, precise, and authoritative. Symmetry and fine lines create gravitas.
> Mapped industries: 05-finance

---

## 1. Layout Skeleton

```
┌──────────────────────────────────────────────────────┐
│                                                      │
│            ┌──────────────────────┐                  │
│            │   TITLE (centered)   │                  │
│            │   ═══════════════    │                  │
│            └──────────────────────┘                  │
│                                                      │
│   ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐  │
│   │ KPI     │ │ KPI     │ │ KPI     │ │ KPI     │  │
│   │ (frame) │ │ (frame) │ │ (frame) │ │ (frame) │  │
│   └─────────┘ └─────────┘ └─────────┘ └─────────┘  │
│                                                      │
│   ┌───────────────────┐  ┌───────────────────┐      │
│   │                   │  │                   │      │
│   │  CHART A (large)  │  │  CHART B (large)  │      │
│   │                   │  │                   │      │
│   └───────────────────┘  └───────────────────┘      │
│                                                      │
│   ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐      │
│   │CHART C │ │CHART D │ │CHART E │ │CHART F │      │
│   │(small) │ │(small) │ │(small) │ │(small) │      │
│   └────────┘ └────────┘ └────────┘ └────────┘      │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### CSS Implementation

Strict symmetry. Central axis holds the most important element. Left and right mirror perfectly. Equal spacing throughout (uniform `gap: 16px`). Three layers: title layer, KPI layer, chart layer (2 large + 4 small symmetric).

```css
.dashboard {
  display: flex;
  flex-direction: column;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  padding: 16px;
  gap: 16px;
  align-items: center;
}
.row {
  display: flex;
  width: 100%;
  gap: 16px;
  justify-content: center;
}
.row-kpi { justify-content: center; }
.row-charts-large {
  flex: 1;
}
.row-charts-small {
  flex: 0 0 auto;
}
.row-charts-large > *,
.row-charts-small > * {
  flex: 1;
}
```

### Header (Title Layer)

```css
.header {
  text-align: center;
  padding: 8px 0 12px 0;
}
.header h1 {
  font-family: 'Noto Serif SC', 'PingFang SC', 'Microsoft YaHei', serif;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 6px;
  text-transform: uppercase;
  color: var(--bezel-text-strong);
  margin: 0;
}
.header .decorative-line {
  width: 60px;
  height: 2px;
  background: var(--bezel-accent-primary);
  margin: 8px auto 0 auto;
}
```

---

## 2. Component Tokens

### :root CSS Variables

```css
:root {
  /* Card */
  --card-radius: 2px;
  --card-bg: rgba(var(--bg-rgb), 0.75);
  --card-border: 2px double rgba(var(--accent-primary-rgb), 0.50);
  --card-shadow: inset 0 0 0 4px var(--bezel-bg-app), inset 0 0 0 5px rgba(var(--accent-primary-rgb), 0.20);
  --card-padding: 16px;
  --card-blur: 0;

  /* Motion */
  --motion-duration: 200ms;
  --motion-easing: linear;
  --motion-ambient: none;
}

@media (prefers-reduced-motion: reduce) {
  :root {
    --motion-duration: 0ms;
    --motion-ambient: none;
  }
}
```

### Card Component

Fine borders -- double-line. Minimal radius (2px). Inner frame structure (padding area contains another border). Gold/silver line accents. High background opacity (0.75). No backdrop-filter blur.

```css
.card {
  background: var(--card-bg);
  border-radius: var(--card-radius);
  border: var(--card-border);
  padding: var(--card-padding);
  display: flex;
  flex-direction: column;
  position: relative;
  box-shadow: var(--card-shadow);
  transition: border-color var(--motion-duration) var(--motion-easing),
              box-shadow var(--motion-duration) var(--motion-easing);
}
/* Inner frame: a second border inside the padding area */
.card::after {
  content: '';
  position: absolute;
  inset: 6px;
  border: 1px solid rgba(var(--accent-primary-rgb), 0.15);
  border-radius: 1px;
  pointer-events: none;
}
.card:hover {
  border-color: rgba(var(--accent-primary-rgb), 0.70);
  box-shadow: inset 0 0 0 4px var(--bezel-bg-app), inset 0 0 0 5px rgba(var(--accent-primary-rgb), 0.35);
}
```

### Card Title

```css
.card-title {
  font-family: 'Noto Serif SC', 'PingFang SC', 'Microsoft YaHei', serif;
  font-size: 13px;
  font-weight: 400;
  color: var(--bezel-text-strong);
  text-align: center;
  letter-spacing: 3px;
  text-transform: uppercase;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(var(--accent-primary-rgb), 0.25);
}
```

Note: Serif font, centered, uppercase, decorative line below.

### KPI -- Refined Frame

Centered symmetric row. Double-line or gold-line border. Serif numerals. Center-aligned.

```css
.kpi-frame {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 14px 20px;
  border: 2px double rgba(var(--accent-primary-rgb), 0.40);
  border-radius: 2px;
  background: rgba(var(--bg-rgb), 0.60);
  min-width: 140px;
  position: relative;
}
.kpi-frame::after {
  content: '';
  position: absolute;
  inset: 4px;
  border: 1px solid rgba(var(--accent-primary-rgb), 0.15);
  pointer-events: none;
}
.kpi-frame .value {
  font-family: 'Noto Serif SC', 'PingFang SC', 'Microsoft YaHei', serif;
  font-size: 28px;
  font-weight: 700;
  color: var(--bezel-text-strong);
  font-variant-numeric: tabular-nums;
  text-align: center;
}
.kpi-frame .label {
  font-family: 'Noto Serif SC', 'PingFang SC', 'Microsoft YaHei', serif;
  font-size: 11px;
  color: var(--bezel-text-muted);
  letter-spacing: 2px;
  text-transform: uppercase;
  text-align: center;
  margin-top: 4px;
}
```

---

## 3. Chart Configuration

### Recommended Chart Types

| Priority | Type | Usage |
|----------|------|-------|
| 1 | bar (classic, no gradient) | Quantity comparison |
| 2 | line (precise, no area fill) | Trends with point markers |
| 3 | table (refined gridlines) | Detailed data display |
| 4 | pie (minimal, donut) | Category distribution |
| 5 | scatter (precise points) | Correlation analysis |

### ECharts Option Templates

**Classic Bar:**

```js
{
  type: 'bar',
  itemStyle: {
    color: 'var(--bezel-accent-primary)',
    borderRadius: 0
  },
  barWidth: '50%',
  emphasis: {
    itemStyle: { color: 'var(--bezel-accent-secondary)' }
  },
  label: {
    show: true,
    position: 'top',
    fontSize: 11,
    fontFamily: 'Noto Serif SC, serif',
    color: 'var(--bezel-text-muted)'
  }
}
```

**Precise Line:**

```js
{
  type: 'line',
  smooth: false,
  symbol: 'circle',
  symbolSize: 6,
  showSymbol: true,
  lineStyle: {
    width: 2,
    color: 'var(--bezel-accent-primary)'
  },
  itemStyle: {
    color: 'var(--bezel-accent-primary)',
    borderWidth: 2,
    borderColor: '#fff'
  },
  areaStyle: null // no area fill
}
```

**Refined Table (using ECharts dataset + series type custom):**

```js
// For tabular data, use a custom HTML table styled with Monument tokens
// rather than an ECharts chart. Example CSS:
{
  borderCollapse: 'collapse',
  fontSize: '12px',
  fontFamily: 'Noto Serif SC, serif',
  th: {
    borderBottom: '2px double rgba(var(--accent-primary-rgb), 0.30)',
    color: 'var(--bezel-text-muted)',
    letterSpacing: '2px',
    textTransform: 'uppercase',
    padding: '8px 12px',
    textAlign: 'center'
  },
  td: {
    borderBottom: '1px solid rgba(var(--accent-primary-rgb), 0.10)',
    padding: '6px 12px',
    color: 'var(--bezel-text-strong)',
    textAlign: 'center'
  }
}
```

### Color Mapping

Solid fills only. No gradients. No border radius on bars. Serif font for all numerals. Industry palette maps directly -- finance uses gold primary, blue secondary. Classic and restrained.

---

## 4. Background

### Implementation

Dark but warm (deep navy or deep brown). Very faint regular texture (diamond/lattice pattern at low opacity) via SVG pattern inlined in CSS.

**HTML structure:**

```html
<div class="bg-texture"></div>
<div class="dashboard">
  <!-- symmetric layout -->
</div>
```

**CSS:**

```css
body {
  margin: 0;
  padding: 0;
  background: var(--bezel-bg-app);
  font-family: 'Noto Serif SC', 'PingFang SC', 'Microsoft YaHei', serif;
  color: var(--bezel-text-strong);
}
.bg-texture {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: 0.04;
  background-image: url("data:image/svg+xml,%3Csvg width='20' height='20' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M10 0L20 10L10 20L0 10Z' fill='none' stroke='rgba(255,255,255,0.5)' stroke-width='0.5'/%3E%3C/svg%3E");
  background-repeat: repeat;
  background-size: 20px 20px;
}
```

### Font CDN

Monument requires Noto Serif SC loaded from Google Fonts CDN. Add to HTML `<head>`:

```html
<link href="https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400;700&display=swap" rel="stylesheet">
```

This requires CSP `font-src` to include `https://fonts.googleapis.com https://fonts.gstatic.com`.

---

## 5. Motion

### Ambient

None. No decorative animation. Only number updates and necessary state changes.

### Hover / Transition

Inner frame line brightens on hover. Duration 200ms, linear easing.

```css
.card:hover {
  border-color: rgba(var(--accent-primary-rgb), 0.70);
  box-shadow: inset 0 0 0 4px var(--bezel-bg-app), inset 0 0 0 5px rgba(var(--accent-primary-rgb), 0.35);
}
```

### KPI Count-up

Restrained and precise. Fast duration, no flourish.

```js
function countUp(el, target, duration) {
  duration = duration || 600;
  var start = 0;
  var startTime = null;
  function tick(ts) {
    if (!startTime) startTime = ts;
    var p = Math.min((ts - startTime) / duration, 1);
    el.textContent = (target * p).toFixed(el.dataset.decimal || 0) + (el.dataset.suffix || '');
    if (p < 1) requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
}
```

---

## 6. Accessibility Checklist

- [ ] All text meets WCAG AA 4.5:1 against card background (rgba 0.75 opacity). High bg opacity provides strong contrast.
- [ ] Noto Serif SC font fallback chain: `'Noto Serif SC', 'PingFang SC', 'Microsoft YaHei', serif` -- must include system fonts for environments without CDN access.
- [ ] `prefers-reduced-motion` disables hover transitions (see CSS above). No ambient animations to disable.
- [ ] No focus-visible concerns -- Monument dashboards are display-only (no interactive controls).
- [ ] Symmetric layout must maintain logical reading order in DOM (not rely on visual symmetry alone).

---

## 7. Performance Notes

- No canvas, no blur, no animation loops. Best performance alongside Mosaic.
- SVG diamond texture is a tiny inline data URI -- no network request, negligible memory.
- Double border + inner frame shadow uses standard CSS box-shadow -- GPU composited.
- Font load: Noto Serif SC is ~200KB subset. Use `font-display: swap` (included in Google Fonts URL) to prevent FOIT.

---

## 8. Token Self-Check

| Token | Value |
|-------|-------|
| --card-radius | 2px |
| --card-bg opacity | 0.75 |
| --card-border | 2px double rgba 0.50 |
| --card-shadow | inner-frame (inset 4px + inset 5px) |
| --card-padding | 16px |
| --card-blur | 0 |
| --card-hover | inner-frame line brighten |
| --motion-duration | 200ms |
| --motion-easing | linear |
| --motion-ambient | none |
