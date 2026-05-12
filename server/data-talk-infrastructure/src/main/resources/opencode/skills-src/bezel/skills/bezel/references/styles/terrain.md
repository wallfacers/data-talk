# Terrain Style Specification

> Visual personality: Data as part of nature. Natural forms and earth-tone palettes express information.
> Mapped industries: 09-energy, 11-agriculture

---

## 1. Layout Skeleton

```
┌──────────────────────────────────────────────────────┐
│              header (left-aligned + icon)             │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ┌──────────────┐  ┌────────────────────────────┐   │
│  │              │  │                            │   │
│  │   LEFT       │  │     RIGHT (65%)            │   │
│  │   STACK      │  │     ┌────────────────┐     │   │
│  │   (35%)      │  │     │  MAP / HEATMAP │     │   │
│  │              │  │     │  (large)        │     │   │
│  │  ┌────────┐  │  │     └────────────────┘     │   │
│  │  │widget 1│  │  │                            │   │
│  │  └────────┘  │  │     ┌──────┬──────┬──────┐ │   │
│  │  ┌────────┐  │  │     │KPI 1 │KPI 2 │KPI 3 │ │   │
│  │  │widget 2│  │  │     │+icon │+icon │+icon │ │   │
│  │  └────────┘  │  │     └──────┴──────┴──────┘ │   │
│  │  ┌────────┐  │  │                            │   │
│  │  │widget 3│  │  └────────────────────────────┘   │
│  │  └────────┘  │                                    │
│  └──────────────┘                                    │
│  ░░░░░░░░░░░░ ground gradient fade ░░░░░░░░░░░░░░░ │
└──────────────────────────────────────────────────────┘
```

### CSS Implementation

2-column flex layout: left narrow (35%) + right wide (65%). Left column: stacked widgets (elevated and depressed). Right column: large map/heatmap on top, KPI row on bottom. Page bottom has gradient fade to "ground" color.

```css
.dashboard {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  gap: 12px;
  padding: 12px;
  position: relative;
  z-index: 1;
}
.column-left {
  flex: 0 0 35%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}
.column-right {
  flex: 0 0 65%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}
.column-right .widget-map {
  flex: 1;
}
.column-right .row-kpi {
  flex: 0 0 auto;
  display: flex;
  gap: 10px;
}
```

### Header

```css
.header {
  width: 100%;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.header h1 {
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 2px;
  color: var(--bezel-text-strong);
}
```

### Ground Fade

```css
.dashboard::after {
  content: '';
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: linear-gradient(transparent, rgba(var(--ground-rgb, 26,18,9), 0.40));
  pointer-events: none;
  z-index: 2;
}
```

---

## 2. Component Tokens

### :root CSS Variables

```css
:root {
  /* Card */
  --card-radius: 14px;
  --card-bg: rgba(var(--bg-rgb), 0.55);
  --card-border: 1px solid rgba(var(--accent-primary-rgb), 0.18);
  --card-shadow: 0 4px 12px rgba(0,0,0,0.12), 0 2px 4px rgba(0,0,0,0.08);
  --card-padding: 14px;
  --card-blur: 10px;

  /* Motion */
  --motion-duration: 350ms;
  --motion-easing: ease-in-out;
  --motion-ambient: ripple 5s ease-in-out infinite;

  /* Decorative */
  --deco-svg: none; /* overridden per industry with inline SVG data URI */
}

@media (prefers-reduced-motion: reduce) {
  :root {
    --motion-duration: 0ms;
    --motion-ambient: none;
  }
}
```

### Card Component

Medium-large radius (14px). Bottom "grounding" shadow via gradient. Colors from soil/vegetation/sky palettes.

```css
.card {
  background: var(--card-bg);
  border-radius: var(--card-radius);
  border: var(--card-border);
  backdrop-filter: blur(var(--card-blur));
  padding: var(--card-padding);
  display: flex;
  flex-direction: column;
  position: relative;
  box-shadow: var(--card-shadow);
  transition: box-shadow var(--motion-duration) var(--motion-easing);
}
.card:hover {
  box-shadow: 0 6px 16px rgba(0,0,0,0.18), 0 3px 6px rgba(0,0,0,0.12);
}
```

### Decorative Element (::after pseudo-element)

Each card has a small decorative SVG icon in the top-right corner, positioned via `::after`. The specific SVG path is provided by the industry file.

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
  pointer-events: none;
}
```

Where `--deco-svg` is an inline SVG data URI provided by each industry file:
- Agriculture: leaf silhouette
- Energy: lightning bolt

### Card Title

```css
.card-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--bezel-text-strong);
  text-align: left;
  letter-spacing: 1px;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.card-title::before {
  content: '';
  display: inline-block;
  width: 14px;
  height: 14px;
  background: var(--title-icon-svg) no-repeat center;
  background-size: contain;
  opacity: 0.6;
}
```

Where `--title-icon-svg` is a small SVG data URI icon for the card type (e.g., map pin, chart bar, leaf).

### KPI -- Icon + Number Card

Bottom horizontal row. SVG data URI natural icons. Slight floating feel.

```css
.kpi-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px 10px;
  background: rgba(var(--bg-rgb), 0.40);
  border-radius: 12px;
  border: 1px solid rgba(var(--accent-primary-rgb), 0.12);
  min-width: 100px;
  flex: 1;
  position: relative;
}
.kpi-card .icon {
  width: 22px;
  height: 22px;
  margin-bottom: 4px;
}
.kpi-card .value {
  font-size: 20px;
  font-weight: 700;
  color: var(--bezel-text-strong);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.kpi-card .label {
  font-size: 10px;
  color: var(--bezel-text-muted);
  margin-top: 2px;
}
```

---

## 3. Chart Configuration

### Recommended Chart Types

| Priority | Type | Usage |
|----------|------|-------|
| 1 | map (geo) | Geographic distribution |
| 2 | heatmap | Matrix / spatial data |
| 3 | effectScatter (ripple) | Point data with ripple |
| 4 | pictorialBar (nature SVG) | Quantity with natural elements |
| 5 | scatter | Correlation analysis |

### ECharts Option Templates

**Map (geo):**

```js
{
  type: 'map',
  map: 'china', // or other geo as specified by industry
  roam: false,
  label: { show: false },
  itemStyle: {
    areaColor: 'rgba(var(--accent-primary-rgb), 0.08)',
    borderColor: 'rgba(var(--accent-primary-rgb), 0.25)',
    borderWidth: 1
  },
  emphasis: {
    itemStyle: {
      areaColor: 'rgba(var(--accent-primary-rgb), 0.20)'
    }
  }
}
```

**EffectScatter (ripple):**

```js
{
  type: 'effectScatter',
  coordinateSystem: 'geo',
  rippleEffect: {
    brushType: 'stroke',
    scale: 3,
    period: 4
  },
  symbolSize: function(val) { return Math.max(val[2] / 2, 6); },
  itemStyle: {
    color: 'var(--bezel-accent-primary)'
  },
  label: {
    show: true,
    fontSize: 10,
    color: 'var(--bezel-text-strong)',
    formatter: '{b}'
  }
}
```

**Heatmap:**

```js
{
  type: 'heatmap',
  itemStyle: {
    borderColor: 'var(--bezel-bg-app)',
    borderWidth: 1
  },
  label: {
    show: true,
    fontSize: 10,
    color: 'var(--bezel-text-strong)'
  },
  emphasis: {
    itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.3)' }
  }
}
```

**PictorialBar (natural element SVG):**

```js
{
  type: 'pictorialBar',
  symbol: 'path://M12,2C9,2 6,5 6,8C6,14 12,22 12,22C12,22 18,14 18,8C18,5 15,2 12,2Z', // leaf path
  symbolSize: [14, 20],
  symbolRepeat: true,
  symbolMargin: 1,
  itemStyle: {
    color: 'var(--bezel-accent-primary)',
    opacity: 0.7
  },
  emphasis: {
    itemStyle: { opacity: 1 }
  }
}
```

### Color Mapping

Natural color palettes from earth/vegetation/sky tones. Agriculture: amber primary + green secondary. Energy: green primary + sky blue secondary. PictorialBar symbols use SVG paths representing natural elements.

---

## 4. Background

### Implementation

Bottom-to-top vertical gradient (dark ground to lighter sky). Optional static satellite map texture as base layer. Subtle wave at bottom edge.

**HTML structure:**

```html
<div class="bg-gradient"></div>
<div class="bg-wave"></div>
<div class="dashboard">
  <!-- 2-column layout -->
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
.bg-gradient {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: linear-gradient(
    0deg,
    rgba(var(--ground-rgb, 26,18,9), 0.30) 0%,
    var(--bezel-bg-app) 40%,
    rgba(var(--accent-primary-rgb), 0.04) 80%,
    rgba(var(--accent-secondary-rgb), 0.06) 100%
  );
}
.bg-wave {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 40px;
  z-index: 0;
  pointer-events: none;
  background: url("data:image/svg+xml,%3Csvg viewBox='0 0 1440 40' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M0,20 C240,35 480,5 720,20 C960,35 1200,5 1440,20 L1440,40 L0,40Z' fill='rgba(var(--accent-primary-rgb),0.06)'/%3E%3C/svg%3E") repeat-x bottom;
  background-size: 1440px 40px;
  animation: wave-shift 8s linear infinite;
}
@keyframes wave-shift {
  from { background-position-x: 0; }
  to { background-position-x: 1440px; }
}
@media (prefers-reduced-motion: reduce) {
  .bg-wave { animation: none; }
}
```

---

## 5. Motion

### Ambient

Water ripple spread. Gentle rhythm. 350ms duration, ease-in-out. Applied via ECharts effectScatter ripple effect (see Chart Configuration). The wave SVG background also shifts subtly.

### Hover / Transition

Cards intensify bottom shadow on hover. Duration 350ms, ease-in-out.

```css
.card:hover {
  box-shadow: 0 6px 16px rgba(0,0,0,0.18), 0 3px 6px rgba(0,0,0,0.12);
}
```

### Ripple Animation (ambient CSS)

A subtle ripple effect on the map container:

```css
@keyframes ripple {
  0% { transform: scale(1); opacity: 0.3; }
  50% { transform: scale(1.005); opacity: 0.15; }
  100% { transform: scale(1); opacity: 0.3; }
}
.widget-map {
  animation: ripple 5s ease-in-out infinite;
}
@media (prefers-reduced-motion: reduce) {
  .widget-map { animation: none; }
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

- [ ] All text meets WCAG AA 4.5:1 against card background (rgba 0.55 opacity). Moderate bg opacity should provide good contrast -- verify with contrast checker.
- [ ] KPI card values must have `aria-label` with full metric name and value.
- [ ] Decorative SVG icons (`::after`, `::before` pseudo-elements) must have `aria-hidden="true"` or be purely decorative (no information conveyed).
- [ ] `prefers-reduced-motion` disables wave animation, ripple effect, and hover transitions (see CSS above).
- [ ] No focus-visible concerns -- Terrain dashboards are display-only (no interactive controls).
- [ ] 2-column layout must maintain logical DOM reading order (left column before right column).

---

## 7. Performance Notes

- EffectScatter ripple animations are GPU-composited by ECharts. Limit to 20-30 scatter points to avoid frame drops.
- Wave SVG background animation uses `background-position-x` -- lightweight, no repaint needed (composited layer).
- `backdrop-filter: blur(10px)` is moderate. Acceptable on all modern browsers with GPU support.
- PictorialBar with repeated SVG symbols: avoid excessive symbol counts (limit `symbolRepeat` to reasonable amounts).
- Map rendering: use `roam: false` for static display to avoid continuous redraw.

---

## 8. Token Self-Check

| Token | Value |
|-------|-------|
| --card-radius | 14px |
| --card-bg opacity | 0.55 |
| --card-border | 1px solid rgba 0.18 |
| --card-shadow | grounding shadow (0 4px 12px, 0 2px 4px) |
| --card-padding | 14px |
| --card-blur | 10px |
| --card-hover | bottom shadow intensify |
| --motion-duration | 350ms |
| --motion-easing | ease-in-out |
| --motion-ambient | ripple 5s ease-in-out infinite |
| --deco-svg | industry-specific (leaf / lightning bolt) |
