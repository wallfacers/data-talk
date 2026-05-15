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

**Widget placement uses the 12-column CSS Grid defined by `compile-rules.md` — `.bezel-widget` must remain a grid child (never `position: absolute`).** The "orbital" feel is achieved by *choosing grid coordinates* for the core widget and by layering decorative rings as `body::before`/`body::after` pseudo-elements behind the grid.

```css
/* Body is already a 12-column grid from compile-rules.md.
   Decorative orbital rings sit behind every grid child. */
body::before,
body::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  background: radial-gradient(circle at center,
    transparent 28%,
    rgba(var(--accent-primary-rgb), 0.10) 28.3%,
    transparent 28.6%,
    transparent 44%,
    rgba(var(--accent-primary-rgb), 0.07) 44.3%,
    transparent 44.6%);
}
.bezel-widget { z-index: 1; }

/* The "core" widget lives in the center of the grid.
   For an 8-row layout, place the core at rows 3-6, columns 4-9 (1-indexed). */
.bezel-widget.widget-core {
  grid-column: 4 / span 6;
  grid-row: 3 / span 4;
  display: flex;
  align-items: center;
  justify-content: center;
}
```

Surrounding widgets get their normal `grid-column` / `grid-row` from `widget.position` in the JSON — the industry file should pick `{x, y, w, h}` values that visually surround the core widget's cells, instead of overriding to absolute coordinates.

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

### KPI -- Ring Indicator

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

Industry palette colors map directly to chart series. First color = primary series, second = secondary. No gradient fills in Orbital -- solid colors only, leveraging the circular/radial geometry for visual interest.

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

Cards: dashed border transitions to solid border + glow on hover. Duration 400ms, ease-in-out.

```css
.card {
  transition: border var(--motion-duration) var(--motion-easing),
              box-shadow var(--motion-duration) var(--motion-easing);
}
.card:hover {
  border-style: solid;
  border-color: rgba(var(--accent-primary-rgb), 0.55);
  box-shadow: 0 0 20px rgba(var(--accent-primary-rgb), 0.15);
}
```

### KPI Count-up

```js
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
- [ ] No focus-visible concerns -- Orbital dashboards are display-only (no interactive controls).

---

## 7. Performance Notes

- Canvas concentric circles: drawn once on load and on resize only. No animation loop needed.
- Orbit arcs: CSS-only rotation, GPU-composited. `will-change: transform` applied.
- Canvas uses `{ alpha: false }` for performance.
- Cap background animation at 30fps effective (slow CSS rotation is already well below this).

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
| --card-hover | dashed-to-solid + glow |
| --motion-duration | 400ms |
| --motion-easing | ease-in-out |
| --motion-ambient | orbit 12s linear infinite |
