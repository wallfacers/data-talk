# Horizon Style Specification

> Visual personality: Information spreads horizontally like geological strata. Top-to-bottom is strategy-to-detail.
> Mapped industries: 03-manufacturing, 06-logistics

---

## 1. Layout Skeleton

```
┌──────────────────────────────────────────────────────┐
│  ┌──────────────────────────────────────────────┐    │
│  │  STRATEGY BAND (flex: 1) — title + summary   │    │
│  └──────────────────────────────────────────────┘    │
│  ───────────── gradient line ────────────────────── │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐       │
│  │ KPI bar│ │ KPI bar│ │ KPI bar│ │ KPI bar│       │
│  │ label→ │ │ label→ │ │ label→ │ │ label→ │       │
│  └────────┘ └────────┘ └────────┘ └────────┘       │
│  ───────────── gradient line ────────────────────── │
│  ┌────────────────────┐ ┌───────────────────────┐   │
│  │                    │ │                       │   │
│  │   CHART A (flex:1) │ │   CHART B (flex:1)    │   │
│  │   wide flat        │ │   wide flat           │   │
│  │                    │ │                       │   │
│  └────────────────────┘ └───────────────────────┘   │
│  ───────────── gradient line ────────────────────── │
│  ┌────────────────────┐ ┌──────────┐ ┌─────────┐   │
│  │                    │ │          │ │         │   │
│  │   CHART C (flex:2) │ │ DETAIL   │ │ DETAIL  │   │
│  │   wide flat        │ │ (flex:1) │ │ (flex:1)│   │
│  │                    │ │          │ │         │   │
│  └────────────────────┘ └──────────┘ └─────────┘   │
└──────────────────────────────────────────────────────┘
```

### CSS Implementation

3-4 horizontal bands stacked vertically. Each band is a `<section>` with its own internal flex layout. Bands separated by gradient lines or decorative `<hr>`. Band heights proportional via `flex: N`.

```css
.dashboard {
  display: flex;
  flex-direction: column;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  gap: 0;
}
.band {
  display: flex;
  gap: 12px;
  padding: 12px 20px;
  position: relative;
}
.band-strategy { flex: 1; align-items: center; }
.band-kpi { flex: 0 0 auto; flex-direction: row; gap: 10px; }
.band-charts { flex: 3; }
.band-detail { flex: 2; }
.band + .band {
  border-top: 1px solid rgba(var(--accent-primary-rgb), 0.15);
}
```

### Band Separator (gradient line)

```css
.band-separator {
  height: 1px;
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(var(--accent-primary-rgb), 0.30) 20%,
    rgba(var(--accent-primary-rgb), 0.30) 80%,
    transparent 100%
  );
  margin: 0 20px;
}
```

### Header (Strategy Band)

```css
.header {
  width: 100%;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
}
.header h1 {
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 2px;
  color: var(--bezel-text-strong);
  border-bottom: 2px solid var(--bezel-accent-primary);
  padding-bottom: 4px;
}
```

---

## 2. Component Tokens

### :root CSS Variables

```css
:root {
  /* Card */
  --card-radius: 8px;
  --card-bg: rgba(var(--bg-rgb), 0.50);
  --card-border: none;
  --card-border-bottom: 1px solid rgba(var(--accent-primary-rgb), 0.30);
  --card-shadow: none;
  --card-padding: 12px 20px;
  --card-blur: 8px;

  /* Motion */
  --motion-duration: 250ms;
  --motion-easing: ease-out;
  --motion-ambient: scan 4s ease-in-out infinite;
}

@media (prefers-reduced-motion: reduce) {
  :root {
    --motion-duration: 0ms;
    --motion-ambient: none;
  }
}
```

### Card Component

Wide flat rectangles (width >> height). Content arranged horizontally (label left, value right). Medium radius (8px). Bottom border only. Padding wider horizontally (12px 20px).

```css
.card {
  background: var(--card-bg);
  border-radius: var(--card-radius);
  border-bottom: var(--card-border-bottom);
  backdrop-filter: blur(var(--card-blur));
  padding: var(--card-padding);
  display: flex;
  flex-direction: column;
  position: relative;
  transition: border-bottom-color var(--motion-duration) var(--motion-easing);
}
.card:hover {
  border-bottom-color: rgba(var(--accent-primary-rgb), 0.60);
}
```

### Card Title

```css
.card-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--bezel-text-strong);
  text-align: left;
  letter-spacing: 1px;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid rgba(var(--accent-primary-rgb), 0.20);
}
```

Note: Left-aligned with bottom border accent.

### KPI -- Horizontal Bar Indicator

Wide flat rectangle, label-left value-right, bottom separator. Lives in the KPI band.

```css
.kpi-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: rgba(var(--bg-rgb), 0.35);
  border-bottom: 1px solid rgba(var(--accent-primary-rgb), 0.20);
  border-radius: 6px;
  min-width: 180px;
  flex: 1;
}
.kpi-bar .label {
  font-size: 12px;
  color: var(--bezel-text-muted);
  white-space: nowrap;
}
.kpi-bar .value {
  font-size: 22px;
  font-weight: 700;
  color: var(--bezel-text-strong);
  font-variant-numeric: tabular-nums;
}
.kpi-bar .separator {
  width: 1px;
  height: 24px;
  background: rgba(var(--accent-primary-rgb), 0.20);
  margin: 0 12px;
}
```

---

## 3. Chart Configuration

### Recommended Chart Types

| Priority | Type | Usage |
|----------|------|-------|
| 1 | bar (horizontal) | Quantity comparison |
| 2 | sankey | Flow / process mapping |
| 3 | area (horizontal gradient) | Time-series composition |
| 4 | line (horizontal time axis) | Trends over time |
| 5 | funnel (left-to-right) | Sequential conversion |

### ECharts Option Templates

**Horizontal Bar:**

```js
{
  type: 'bar',
  layout: 'horizontal',
  itemStyle: {
    color: 'var(--bezel-accent-primary)',
    borderRadius: [0, 3, 3, 0]
  },
  barWidth: '55%',
  emphasis: {
    itemStyle: { color: 'var(--bezel-accent-secondary)' }
  },
  label: {
    show: true,
    position: 'right',
    fontSize: 11,
    color: 'var(--bezel-text-muted)'
  }
}
```

**Sankey:**

```js
{
  type: 'sankey',
  orient: 'horizontal',
  nodeAlign: 'left',
  layoutIterations: 32,
  lineStyle: {
    color: 'gradient',
    curveness: 0.5,
    opacity: 0.25
  },
  itemStyle: {
    borderWidth: 0,
    color: 'var(--bezel-accent-primary)'
  },
  label: {
    fontSize: 11,
    color: 'var(--bezel-text-strong)'
  }
}
```

**Area with Horizontal Gradient:**

```js
{
  type: 'line',
  smooth: false,
  areaStyle: {
    color: {
      type: 'linear',
      x: 0, y: 0, x2: 1, y2: 0,
      colorStops: [
        { offset: 0, color: 'rgba(var(--accent-primary-rgb), 0.30)' },
        { offset: 1, color: 'rgba(var(--accent-primary-rgb), 0.02)' }
      ]
    }
  },
  lineStyle: {
    width: 2,
    color: 'var(--bezel-accent-primary)'
  }
}
```

**Funnel (left-to-right):**

```js
{
  type: 'funnel',
  orient: 'horizontal',
  sort: 'descending',
  gap: 4,
  label: {
    fontSize: 11,
    color: 'var(--bezel-text-strong)',
    position: 'inside'
  },
  itemStyle: {
    borderColor: 'var(--bezel-bg-app)',
    borderWidth: 1
  }
}
```

### Grid Configuration

Emphasis on horizontal time axis:

```js
grid: {
  left: 48,
  right: 16,
  top: 24,
  bottom: 32,
  containLabel: false
}
```

### Color Mapping

Horizontal gradients where applicable. Primary series uses accent-primary, secondary uses accent-secondary. No radial or circular geometry.

---

## 4. Background

### Implementation

Dark base + top-to-bottom gradient bands + horizontal contour lines via `repeating-linear-gradient`.

**HTML structure:**

```html
<div class="bg-contour"></div>
<div class="bg-scan"></div>
<div class="dashboard">
  <!-- bands -->
</div>
```

**CSS:**

```css
body {
  margin: 0;
  padding: 0;
  background: var(--bezel-bg-app);
  font-family: var(--bezel-font-stack);
  color: var(--bezel-text-strong);
}
.bg-contour {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent 59px,
      rgba(var(--accent-primary-rgb), 0.06) 59px,
      rgba(var(--accent-primary-rgb), 0.06) 60px
    ),
    linear-gradient(
      180deg,
      var(--bezel-bg-app) 0%,
      rgba(var(--accent-primary-rgb), 0.04) 40%,
      rgba(var(--accent-primary-rgb), 0.08) 60%,
      var(--bezel-bg-app) 100%
    );
}
.bg-scan {
  position: fixed;
  left: 0; right: 0;
  height: 2px;
  background: linear-gradient(90deg,
    transparent,
    rgba(var(--accent-primary-rgb), 0.25),
    transparent
  );
  z-index: 0;
  pointer-events: none;
  animation: scan 4s ease-in-out infinite;
}
@keyframes scan {
  0% { top: 0; opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { top: 100vh; opacity: 0; }
}
@media (prefers-reduced-motion: reduce) {
  .bg-scan { animation: none; display: none; }
}
```

---

## 5. Motion

### Ambient

Horizontal scan line sweeps top-to-bottom over 4s. Continuous loop. Contour lines are static.

### Hover / Transition

Cards brighten bottom border on hover. Duration 250ms, ease-out.

```css
.card:hover {
  border-bottom-color: rgba(var(--accent-primary-rgb), 0.60);
}
```

### KPI Count-up

```js
function countUp(el, target, duration) {
  duration = duration || 1000;
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

- [ ] All text meets WCAG AA 4.5:1 against card background (rgba 0.50 opacity). Moderate bg opacity should provide adequate contrast -- verify with contrast checker.
- [ ] KPI bar values must have `aria-label` with full metric name and value.
- [ ] `prefers-reduced-motion` disables scan line animation and hover transitions (see CSS above).
- [ ] No focus-visible concerns -- Horizon dashboards are display-only (no interactive controls).
- [ ] Horizontal layout must maintain reading order for screen readers (left-to-right label-value flow).

---

## 7. Performance Notes

- Background uses CSS gradients only -- no canvas, minimal performance impact.
- Scan line animation is a single `div` with `top` animation. Use `will-change: top, opacity` for GPU compositing.
- Contour lines are static `repeating-linear-gradient` -- no repaint cost.
- `backdrop-filter: blur(8px)` is moderate. Acceptable on all modern browsers with GPU support.

---

## 8. Token Self-Check

| Token | Value |
|-------|-------|
| --card-radius | 8px |
| --card-bg opacity | 0.50 |
| --card-border | none (bottom only: 1px solid rgba 0.30) |
| --card-shadow | none |
| --card-padding | 12px 20px |
| --card-blur | 8px |
| --card-hover | bottom border brighten to rgba 0.60 |
| --motion-duration | 250ms |
| --motion-easing | ease-out |
| --motion-ambient | scan 4s ease-in-out infinite |
