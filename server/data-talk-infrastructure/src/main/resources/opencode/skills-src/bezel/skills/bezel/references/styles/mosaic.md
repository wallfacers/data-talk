# Mosaic Style Specification

> Visual personality: Data as differently-sized blocks. Bento Grid inspired -- maximum information density with visual rhythm.
> Mapped industries: 02-ecommerce, 04-saas

---

## 1. Layout Skeleton

```
┌──────────────────────────────────────────────────┐
│ ┌──────────────┬───────┬───────┬───────────────┐ │
│ │  TITLE CELL  │ KPI 1 │ KPI 2 │   KPI 3       │ │
│ ├──────────────┼───────┴───────┼───────────────┤ │
│ │              │               │               │ │
│ │   CHART A    │   CHART B     │   KPI 4       │ │
│ │   (2x wide)  │               │   + sparkline │ │
│ │              │               │               │ │
│ ├──────────────┼───────┬───────┼───────────────┤ │
│ │   KPI 5      │KPI 6  │KPI 7  │   CHART C     │ │
│ │   + sparkline│       │       │               │ │
│ ├──────────────┴───────┴───────┼───────────────┤ │
│ │                              │               │ │
│ │        CHART D               │   CHART E     │ │
│ │                              │               │ │
│ └──────────────────────────────┴───────────────┘ │
└──────────────────────────────────────────────────┘
```

### CSS Implementation

No separate header or footer bar. The title embeds in the first grid cell. KPIs live as small cells in the grid. The `grid-template-areas` property defines irregular block sizing per industry file.

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
/* grid-template-areas defined per industry file, e.g.:
   "title  kpi1   kpi2   kpi3"
   "chartA chartA chartB kpi4"
   "kpi5   kpi6   kpi7   chartC"
   "chartD chartD chartD chartE"
*/
```

### No Header Bar

Title occupies the first grid cell instead of a separate header element:

```css
.cell-title {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 12px;
}
.cell-title h1 {
  font-size: 18px;
  font-weight: 600;
  color: var(--bezel-text-strong);
  letter-spacing: 2px;
}
.cell-title .clock {
  font-size: 12px;
  color: var(--bezel-text-muted);
  font-variant-numeric: tabular-nums;
}
```

---

## 2. Component Tokens

### :root CSS Variables

```css
:root {
  /* Card */
  --card-radius: 4px;
  --card-bg: rgba(var(--bg-rgb), 0.85);
  --card-border: none;
  --card-shadow: none;
  --card-padding: 10px;
  --card-blur: 0;

  /* Motion */
  --motion-duration: 150ms;
  --motion-easing: cubic-bezier(0.4, 0, 0.2, 1);
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

```css
.card {
  background: var(--card-bg);
  border-radius: var(--card-radius);
  border: var(--card-border);
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

### Card Title

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

Note: Embedded top-left in card. No `::before` bar. No extra vertical space.

### KPI -- Number Block

KPIs are compact number + tiny label cells embedded directly in the grid. No border -- distinguished by background color alone. Large font (32px+).

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

---

## 3. Chart Configuration

### Recommended Chart Types

| Priority | Type | Usage |
|----------|------|-------|
| 1 | bar (square corners, solid fill) | Quantity comparison |
| 2 | heatmap | Matrix data |
| 3 | treemap | Hierarchical composition |
| 4 | sparkline | KPI trend inline |
| 5 | line (step) | Step-line trends |

### ECharts Option Templates

**Bar (Mosaic style -- square, solid, no gradient):**

```js
{
  type: 'bar',
  itemStyle: {
    color: 'var(--bezel-accent-primary)',
    borderRadius: [0, 0, 0, 0]
  },
  barWidth: '60%',
  emphasis: {
    itemStyle: { color: 'var(--bezel-accent-secondary)' }
  }
}
```

**Heatmap:**

```js
{
  type: 'heatmap',
  itemStyle: {
    borderColor: 'var(--bezel-bg-app)',
    borderWidth: 2
  },
  emphasis: {
    itemStyle: { shadowBlur: 6, shadowColor: 'rgba(0,0,0,0.3)' }
  }
}
```

**Sparkline (inline in KPI cell):**

```js
{
  type: 'line',
  symbol: 'none',
  smooth: false,
  lineStyle: { width: 1.5, color: 'var(--bezel-accent-primary)' },
  areaStyle: { color: 'rgba(var(--accent-primary-rgb), 0.10)' },
  grid: { left: 0, right: 0, top: 4, bottom: 4 },
  xAxis: { show: false, type: 'category' },
  yAxis: { show: false, type: 'value' }
}
```

**Treemap:**

```js
{
  type: 'treemap',
  breadcrumb: { show: false },
  itemStyle: {
    borderColor: 'var(--bezel-bg-app)',
    borderWidth: 2,
    gapWidth: 2
  },
  label: {
    fontSize: 11,
    color: 'var(--bezel-text-strong)'
  }
}
```

### Grid Configuration

Minimal grid padding to maximize data density:

```js
grid: {
  left: 32,
  right: 8,
  top: 16,
  bottom: 24,
  containLabel: false
}
```

### Color Mapping

Solid fills only. No gradients. Industry palette maps directly: first color = primary series, second = secondary series. Step lines instead of curves everywhere.

---

## 4. Background

### Implementation

Solid color or very subtle linear gradient. No particles, no texture, no blur. Let the blocks speak.

**HTML structure:**

```html
<div class="dashboard">
  <!-- grid cells -->
</div>
```

**CSS:**

```css
body {
  margin: 0;
  padding: 0;
  background: linear-gradient(135deg, var(--bezel-bg-app) 0%, rgba(var(--accent-primary-rgb), 0.03) 100%);
  font-family: var(--bezel-font-stack);
  color: var(--bezel-text-strong);
}
```

No additional background elements needed. The grid cells themselves provide visual structure.

---

## 5. Motion

### Ambient

None. No floating animations, no particles, no ambient motion. Static and precise.

### Hover / Transition

Cards lift content 2px up on hover. Duration 150ms, `cubic-bezier(0.4, 0, 0.2, 1)` (Material ease).

```css
.card:hover {
  transform: translateY(-2px);
}
```

### KPI Count-up

Fast, precise number animations. No easing flourish.

```js
function countUp(el, target, duration) {
  duration = duration || 800;
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

- [ ] All text meets WCAG AA 4.5:1 against card background (rgba 0.85 opacity). High bg opacity provides good contrast by default.
- [ ] Verify muted label text (11px uppercase) meets 4.5:1 -- small font + muted color is the highest-risk combination.
- [ ] `prefers-reduced-motion` disables hover translateY transition (see CSS above).
- [ ] No focus-visible concerns -- Mosaic dashboards are display-only (no interactive controls).
- [ ] Grid cells must have `role="region"` and `aria-label` for screen reader navigation.

---

## 7. Performance Notes

- No canvas, no blur, no filter effects -- best performance of all 6 styles.
- CSS Grid layout is GPU-accelerated in all modern browsers.
- Hover transform uses `translateY` which triggers compositing only (no repaint).
- No `will-change` needed since transitions are lightweight.

---

## 8. Token Self-Check

| Token | Value |
|-------|-------|
| --card-radius | 4px |
| --card-bg opacity | 0.85 |
| --card-border | none |
| --card-shadow | none |
| --card-padding | 10px |
| --card-blur | 0 |
| --card-hover | content translateY(-2px) |
| --motion-duration | 150ms |
| --motion-easing | cubic-bezier(0.4, 0, 0.2, 1) |
| --motion-ambient | none |
