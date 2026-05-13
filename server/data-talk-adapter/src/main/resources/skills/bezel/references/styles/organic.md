# Organic Style Specification

> Visual personality: People are central. Data serves humans. Visuals breathe with warmth and generosity.
> Mapped industries: 07-healthcare, 08-hr, 12-education

---

## 1. Layout Skeleton

```
┌──────────────────────────────────────────────────────┐
│              header (soft, wide padding)              │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ┌─────────────────────┐  ┌──────────────────┐      │
│  │                     │  │                  │      │
│  │    WIDGET (flex:2)  │  │   WIDGET(flex:1) │      │
│  │    wide             │  │   narrow         │      │
│  │                     │  │                  │      │
│  └─────────────────────┘  └──────────────────┘      │
│                                                      │
│      ┌────────┐  ┌────────┐  ┌──────────┐           │
│      │KPI soft│  │KPI soft│  │KPI soft  │           │
│      │ + icon │  │ + icon │  │ + icon   │           │
│      │ + desc │  │ + desc │  │ + desc   │           │
│      └────────┘  └────────┘  └──────────┘           │
│                                                      │
│  ┌──────────┐  ┌───────────────────────────────┐    │
│  │ WIDGET   │  │       WIDGET (flex:2)         │    │
│  │ (flex:1) │  │       wide                    │    │
│  └──────────┘  └───────────────────────────────┘    │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### CSS Implementation

Irregular spacing via `flex-wrap`. Widget widths unequal -- some `flex: 2`, some `flex: 1`. Varied gaps. Some elements get extra margin for breathing room. 20-30% more whitespace than other styles.

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

### Header

```css
.header {
  width: 100%;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.header h1 {
  font-size: 20px;
  font-weight: 400;
  letter-spacing: 3px;
  color: var(--bezel-text-strong);
}
```

---

## 2. Component Tokens

### :root CSS Variables

```css
:root {
  /* Card */
  --card-radius: 18px;
  --card-bg: rgba(var(--bg-rgb), 0.40);
  --card-border: 1px solid rgba(var(--accent-primary-rgb), 0.12);
  --card-shadow: 0 2px 8px rgba(0,0,0,0.08), 0 8px 24px rgba(0,0,0,0.06);
  --card-padding: 20px;
  --card-blur: 14px;

  /* Motion */
  --motion-duration: 500ms;
  --motion-easing: cubic-bezier(0.25, 0.1, 0.25, 1);
  --motion-ambient: breathing 6s ease-in-out infinite;
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

### Card Title

```css
.card-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--bezel-text-strong);
  text-align: left;
  letter-spacing: 0.5px;
  margin-bottom: 10px;
}
```

Note: Left-aligned, 14px, rounded feel. Soft muted colors.

### KPI -- Soft Card

KPIs use soft cards with icon + description. Flexible placement, not forced into one row.

```css
.kpi-soft {
  background: rgba(var(--bg-rgb), 0.25);
  border-radius: 14px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 120px;
}
.kpi-soft .icon {
  width: 28px;
  height: 28px;
  color: var(--bezel-accent-primary);
  margin-bottom: 4px;
}
.kpi-soft .value {
  font-size: 24px;
  font-weight: 700;
  color: var(--bezel-text-strong);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.kpi-soft .label {
  font-size: 12px;
  color: var(--bezel-text-muted);
}
.kpi-soft .description {
  font-size: 11px;
  color: var(--bezel-text-muted);
  opacity: 0.7;
  margin-top: 2px;
}
```

---

## 3. Chart Configuration

### Recommended Chart Types

| Priority | Type | Usage |
|----------|------|-------|
| 1 | line (smooth) | Trends with soft curves |
| 2 | bar (pictorialBar) | Human/object icon bars |
| 3 | liquidFill | Water ripple progress |
| 4 | pie (rounded donut) | Category distribution |
| 5 | area (soft gradient) | Composition over time |

### ECharts Option Templates

**Smooth Line:**

```js
{
  type: 'line',
  smooth: true,
  symbol: 'circle',
  symbolSize: 6,
  lineStyle: {
    width: 2.5,
    color: 'var(--bezel-accent-primary)'
  },
  itemStyle: {
    color: 'var(--bezel-accent-primary)',
    borderWidth: 2,
    borderColor: '#fff'
  },
  areaStyle: {
    color: {
      type: 'linear',
      x: 0, y: 0, x2: 0, y2: 1,
      colorStops: [
        { offset: 0, color: 'rgba(var(--accent-primary-rgb), 0.25)' },
        { offset: 1, color: 'rgba(var(--accent-primary-rgb), 0.02)' }
      ]
    }
  }
}
```

**PictorialBar (human/object icons):**

```js
{
  type: 'pictorialBar',
  symbolRepeat: true,
  symbolSize: [12, 6],
  symbolMargin: 2,
  itemStyle: {
    color: 'var(--bezel-accent-primary)',
    opacity: 0.7
  },
  emphasis: {
    itemStyle: { opacity: 1 }
  }
}
```

**LiquidFill (requires echarts-liquidfill extension):**

```js
{
  type: 'liquidFill',
  radius: '75%',
  data: [0.65],
  amplitude: 8,
  waveLength: '80%',
  phase: 0,
  period: 3000,
  color: ['rgba(var(--accent-primary-rgb), 0.5)'],
  outline: {
    show: true,
    borderDistance: 4,
    itemStyle: {
      borderColor: 'rgba(var(--accent-primary-rgb), 0.30)',
      borderWidth: 2
    }
  },
  backgroundStyle: {
    color: 'rgba(var(--bg-rgb), 0.30)'
  },
  label: {
    fontSize: 22,
    fontWeight: 'bold',
    color: 'var(--bezel-text-strong)'
  }
}
```

### Color Mapping

Colors desaturated 15-20% compared to raw palette. Apply `opacity: 0.80` or mix with gray to soften the industry palette. Smooth curves (`smooth: true`) everywhere. Area fills use soft vertical gradients.

---

## 4. Background

### Implementation

Soft color blobs (2-3 large `filter:blur(100px)` circles) + very faint noise texture. Not pure dark -- warm gradient base.

**HTML structure:**

```html
<div class="bg-blob blob-1"></div>
<div class="bg-blob blob-2"></div>
<div class="bg-blob blob-3"></div>
<div class="dashboard">
  <!-- widgets -->
</div>
```

**CSS:**

```css
body {
  margin: 0;
  padding: 0;
  background: linear-gradient(135deg, var(--bezel-bg-app) 0%, rgba(var(--accent-primary-rgb), 0.08) 50%, var(--bezel-bg-app) 100%);
  font-family: var(--bezel-font-stack);
  color: var(--bezel-text-strong);
}
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
.blob-3 {
  width: 35vw; height: 35vw;
  top: 40%; left: 55%;
  background: radial-gradient(circle, rgba(var(--accent-primary-rgb), 0.10), transparent 70%);
  animation: drift3 14s ease-in-out infinite alternate;
}
@keyframes drift1 { to { transform: translate(5vw, 3vw); } }
@keyframes drift2 { to { transform: translate(-4vw, -5vw); } }
@keyframes drift3 { to { transform: translate(-3vw, 4vw); } }
@media (prefers-reduced-motion: reduce) {
  .bg-blob { animation: none; filter: blur(80px); }
}
```

---

## 5. Motion

### Ambient

Slow "breathing" -- subtle scale oscillation on select elements. 500ms duration, `cubic-bezier(0.25, 0.1, 0.25, 1)` easing.

```css
@keyframes breathing {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.008); }
}
.card-breathe {
  animation: breathing 6s ease-in-out infinite;
}
@media (prefers-reduced-motion: reduce) {
  .card-breathe { animation: none; }
}
```

Background blobs drift slowly (see Background section). Soft gradient transitions, no hard-edge motion.

### Hover / Transition

Cards scale slightly and deepen shadow on hover. Duration 500ms, soft easing.

```css
.card:hover {
  transform: scale(1.01);
  box-shadow: 0 2px 8px rgba(0,0,0,0.10), 0 12px 32px rgba(0,0,0,0.10);
}
```

### KPI Count-up

```js
function countUp(el, target, duration) {
  duration = duration || 1500;
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

- [ ] All text meets WCAG AA 4.5:1 against card background. Low bg opacity (0.40) + blur requires explicit contrast verification. Minimum text colors: `--bezel-text-strong` must be at least `#e2e8f0`, `--bezel-text-muted` at least `#94a3b8` to pass 4.5:1 against the blurred card background.
- [ ] KPI soft cards must have `aria-label` with full metric name, value, and description.
- [ ] `prefers-reduced-motion` disables breathing animation, blob drift, and hover transitions (see CSS above).
- [ ] No focus-visible concerns -- Organic dashboards are display-only (no interactive controls).
- [ ] Soft colors must not sacrifice readability -- test with color contrast checker at actual rendered opacity.

---

## 7. Performance Notes

- `filter:blur(100px)` on large divs (60vw x 60vw) can cause frame drops on low-end GPUs. Mitigation: `will-change: transform` applied to all blobs.
- Reduce blur radius to 80px when `prefers-reduced-motion` is active (also disables animation).
- Consider reducing blur to 80px on devices with `navigator.hardwareConcurrency <= 4` as a heuristic for low GPU capability.
- No canvas rendering -- pure CSS + DOM, which is lighter than canvas for this effect.

---

## 8. Token Self-Check

| Token | Value |
|-------|-------|
| --card-radius | 18px |
| --card-bg opacity | 0.40 |
| --card-border | 1px solid rgba 0.12 |
| --card-shadow | multi-layer soft (0 2px 8px, 0 8px 24px) |
| --card-padding | 20px |
| --card-blur | 14px |
| --card-hover | scale(1.01) + shadow deepen |
| --motion-duration | 500ms |
| --motion-easing | cubic-bezier(0.25, 0.1, 0.25, 1) |
| --motion-ambient | breathing 6s ease-in-out infinite |
