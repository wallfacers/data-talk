# Bezel Style Matrix System — Design Spec

> Date: 2026-05-12
> Status: Approved (post-review revision)
> Scope: Rewrite design-language.md, create styles/ directory, slim down industry files, update patterns-catalog.md and SKILL.md

---

## Problem

All 12 industry dashboards share the same structural DNA: Header + KPI Row + 3-column Flex + Footer. Differentiation is limited to accent color and background animation. Every card uses the same glass-morphism pattern, every KPI the same pill/card shape, every chart the same ECharts dark theme configuration. The result is 12 dashboards that look like color swaps of the same template.

## Solution: Style Matrix

Decouple **industry** (data semantics) from **visual style** (layout + components + atmosphere). Define 6 visual personalities, each with a complete set of design tokens spanning layout, card shape, KPI form, chart configuration, background, and motion. Each industry maps to one style via a lookup table.

---

## 1. The 6 Visual Personalities

### 1.1 Orbital

**Philosophy:** Everything revolves around a center. Use circles, arcs, and rings to express hierarchy and flow.

- **Layout:** Center-radiating. Core widget at page center via `position: absolute; top:50%; left:50%; transform:translate(-50%,-50%)`. Surrounding widgets placed by angular coordinates (each industry file specifies top/left percentages).
- **Cards:** Large radius (24px, capsule-like). Dashed borders. No hard edges.
- **Titles:** Centered, lightweight font-weight, arc-aligned or centered.
- **Motion:** Continuous rotation/orbit feel. Orbit lines drift slowly in background.
- **Background:** Dark + concentric circle grid lines (radar screen) + slowly rotating orbit arc SVGs.
- **Charts:** Radar, gauge (270-degree arc), donut. Bar charts use polar coordinate system. Tooltip is circular popup.
- **Industries:** Multi-Screen, Cybersecurity

### 1.2 Mosaic

**Philosophy:** Data as differently-sized blocks. Bento Grid inspired — maximum information density with visual rhythm.

- **Layout:** CSS Grid with unequal proportions. `grid-template-areas` defines irregular blocks. No separate header/footer bar — title embeds in first grid cell. KPIs live as small cells in the grid.
- **Cards:** No border, background color alone distinguishes blocks. Small radius (4px, near square). Background opacity high (0.85). No backdrop-filter blur. Hover lifts content 2px up instead of border glow.
- **Titles:** Embedded top-left in card, 11px uppercase, wide letter-spacing, no extra space.
- **Motion:** Fast, precise number animations and micro-interactions. No large floating animations.
- **Background:** Solid color or very subtle linear gradient. No particles or texture — let the blocks speak.
- **Charts:** Bar (square corners, solid fill, no gradient), heatmap, treemap, sparklines embedded in KPI cells. Step lines instead of curves. Minimal grid padding.
- **Industries:** E-Commerce, SaaS

### 1.3 Horizon

**Philosophy:** Information spreads horizontally like geological strata. Top-to-bottom is strategy-to-detail.

- **Layout:** 3-4 horizontal bands stacked vertically. Each band is a `<section>` with its own internal flex layout. Bands separated by gradient lines or decorative `<hr>`. Band heights proportional via `flex: N`.
- **Cards:** Wide flat rectangles (width >> height). Content arranged horizontally (label left, value right). Medium radius (8px). Bottom border only (no full border). Padding: 12px 20px (wider horizontally).
- **Titles:** Left-aligned with bottom border accent.
- **Motion:** Horizontal scrolling marquee (live data stream), left-to-right scan animation.
- **Background:** Dark + horizontal gradient bands (topographic contour lines) or top-to-bottom lightness gradient.
- **Charts:** Horizontal bars, sankey diagrams, area charts emphasizing horizontal time axis. Funnels arranged left-to-right.
- **Industries:** Logistics, Manufacturing

### 1.4 Organic

**Philosophy:** People are central. Data serves humans. Visuals breathe with warmth and generosity.

- **Layout:** Irregular spacing via `flex-wrap`. Widget widths unequal (some `flex: 2`, some `flex: 1`). Varied gaps — some elements get extra margin for breathing room. Cards may slightly overlap (negative margin). Overall padding larger (16-24px). 20-30% more whitespace than other styles.
- **Cards:** Large radius (18px). Multi-layer soft shadows. Background opacity lower (0.40). Strong backdrop blur (14px). Warm, muted colors. Hover: scale(1.01) + shadow deepening.
- **Titles:** Left-aligned, 14px, rounded feel, soft colors.
- **Motion:** Slow "breathing" (subtle scale oscillation), soft gradient transitions. No hard-edge motion. 500ms duration.
- **Background:** Soft color blobs (2-3 large `filter:blur(100px)` circles) + very faint noise texture. Not pure dark — warm gradient.
- **Charts:** Smooth curves (`smooth: true`), liquid fill (water ripple), pictorialBar with human/object icons. Area fill with soft vertical gradient. Colors desaturated 15-20%.
- **Industries:** HR, Healthcare, Education

### 1.5 Monument

**Philosophy:** Data is solemn, precise, and authoritative. Symmetry and fine lines create gravitas.

- **Layout:** Strict symmetry. Central axis holds the most important element, left and right mirror perfectly. Equal spacing throughout (uniform `gap: 16px`). Three layers: title layer, KPI layer, chart layer (2 large + 4 small symmetric).
- **Cards:** Fine borders — double-line (`border: 2px double`). Minimal radius (2px). Inner frame structure (padding area contains another border). Gold/silver line accents. High background opacity (0.75). No backdrop-filter blur.
- **Titles:** Serif font via CDN (Noto Serif SC, with `'PingFang SC'` as fallback). Uppercase, wide letter-spacing, centered. Decorative line below.
- **Motion:** Extremely restrained — only number updates and necessary state changes. No decorative animation. 200ms duration, linear easing.
- **Background:** Dark but warm (deep navy or deep brown). Very faint regular texture (diamond/lattice pattern at low opacity).
- **Charts:** Classic bar (no gradient, solid fill, `borderRadius: 0`), precise line charts (no area fill, lineWidth: 2, point markers), refined tables with fine gridlines. Serif numerals.
- **Industries:** Finance

> **Why Monument despite only 1 mapped industry:** Finance is the only current industry where the data character (money, regulation, precision) demands this visual personality. However, Monument is architecturally justified as a distinct style because it fills the "formal authority" axis that no other style covers. Future industries like Insurance, Government, Legal, and Audit would map here naturally. It also serves as the canonical example when a custom industry needs a "serious/trusted" visual tone.

### 1.6 Terrain

**Philosophy:** Data as part of nature. Natural forms and earth-tone palettes express information.

- **Layout:** Organic grid with variable row heights — some areas "elevated" (large widgets), some "depressed" (small widgets). Left narrow (35%) + right wide (65%) two-column flex. Right column: large map/heatmap on top, KPI row on bottom. Page bottom has gradient fade to "ground" color.
- **Cards:** Medium-large radius (14px). Bottom "grounding" shadow via gradient. Colors from soil/vegetation/sky palettes.
- **Decorative elements:** Use `::before`/`::after` pseudo-elements with inline SVG data URIs for natural motifs (leaf silhouette, water drop). Each industry file provides the specific SVG path; the style file defines the positioning convention (16x16px, positioned top-right of card, accent color at 0.3 opacity).
- **Titles:** Left-aligned with small icon via `::before` pseudo-element containing an inline SVG data URI.
- **Motion:** Natural rhythm — slow "growth" animation (bottom-to-top expansion), water ripple spread, gentle wind sway. 350ms duration.
- **Background:** Bottom-to-top gradient (dark ground to lighter sky). Optional static satellite map texture as base layer. Subtle wave at bottom edge.
- **Charts:** Map, heatmap, effectScatter (ripple scatter). PictorialBar with natural element SVG paths. Colors from natural palette.
- **Industries:** Agriculture, Energy

---

## 2. Industry-to-Style Mapping

| # | Industry | Style | Rationale |
|---|----------|-------|-----------|
| 01 | Multi-Screen | Orbital | Data hub — all info orbits the core |
| 02 | E-Commerce | Mosaic | Dense ops metrics need Bento density |
| 03 | Manufacturing | Horizon | Production line is horizontal process flow |
| 04 | SaaS | Mosaic | Metric-driven, same density need as e-commerce |
| 05 | Finance | Monument | Financial data demands authority and precision |
| 06 | Logistics | Horizon | Supply chain is end-to-end horizontal flow |
| 07 | Healthcare | Organic | Patient-centric, requires warmth |
| 08 | HR | Organic | Human resources is inherently human-centered |
| 09 | Energy | Terrain | Energy (wind/solar/hydro) is nature-driven |
| 10 | Cybersecurity | Orbital | Situational awareness suits radar/ring visuals |
| 11 | Agriculture | Terrain | Agriculture directly tied to nature |
| 12 | Education | Organic | Education is human-centered |

**Distribution note:** The mapping {Orbital:2, Mosaic:2, Horizon:2, Organic:3, Monument:1, Terrain:2} is intentional. Organic covers more industries because "human-centered" is a broader category (any industry dealing with people as primary entities). Monument covers only Finance because "formal authority" is a narrow, specialized visual personality. This is acceptable — Monument exists to fill that axis, and its value scales when custom industries (Insurance, Government) are added.

---

## 3. Design Token System

### Token Application Mechanism

Each style file contains a complete CSS template that declares all style-specific tokens on `:root`. The compiled HTML applies the style by embedding the style's `:root` block directly — no runtime class switching. The assembly process in compile-rules.md (STEP 3) merges:

1. **Base tokens** from design-language.md (spacing scale, typography, naming conventions)
2. **Style tokens** from `references/styles/<style>.md` (card shape, layout, motion)
3. **Industry color overrides** from `references/industries/<industry>.md` (accent-primary, accent-secondary, bg-app)

The merge order is cascade-based: base → style → industry. Later declarations win, so industry colors override style defaults.

### 3.1 Layout Grammar

Each style has a unique layout implementation:

| Style | CSS Technique | Key Property |
|-------|--------------|-------------|
| Orbital | `position: absolute` with coordinate placement | Widgets positioned by top/left % from industry file |
| Mosaic | `display: grid` + `grid-template-areas` | Irregular block sizing per industry |
| Horizon | Stacked `<section>` flex containers | Band heights via `flex: N` |
| Organic | `flex-wrap` with varied widths and gaps | Extra margin on select elements |
| Monument | Symmetric `flex` with uniform gap | Mirror layout, center element emphasized |
| Terrain | 2-column `flex` (35%/65%) | Left stack + right map area |

### 3.2 Card Shape Tokens

| Token | Orbital | Mosaic | Horizon | Organic | Monument | Terrain |
|-------|---------|--------|---------|---------|----------|---------|
| `--card-radius` | 24px | 4px | 8px | 18px | 2px | 14px |
| `--card-bg` opacity | 0.35 | 0.85 | 0.50 | 0.40 | 0.75 | 0.55 |
| `--card-border` | 1px dashed | none | bottom only 1px | 1px solid 0.12 | 2px double | 1px solid + bottom gradient shadow |
| `--card-shadow` | none | none | none | multi-layer soft | inner-frame shadow | bottom grounding shadow |
| `--card-padding` | 16px | 10px | 12px 20px | 20px | 16px | 14px |
| `--card-blur` | 6px | 0 | 8px | 14px | 0 | 10px |
| `--card-hover` | dashed→solid + glow | content translateY(-2px) | bottom line brighten | scale(1.01) + shadow deepen | inner-frame line brighten | bottom shadow intensify |

### 3.3 KPI Form Tokens

| Style | KPI Form | Layout | Visual Character |
|-------|----------|--------|-----------------|
| Orbital | Ring indicator (number in center of small gauge) | Distributed around center widget | Small gauge/ring progress bars, no dedicated row |
| Mosaic | Number block (compact number + tiny label) | Grid cell embedded in mosaic | No border, distinguished by background color, large font (32px+) |
| Horizon | Horizontal bar indicator | Layer 2 horizontal row | Wide flat rectangle, label-left value-right, bottom separator |
| Organic | Soft card (with icon + description) | Flexible placement, not forced to one row | Large radius, sub-label description, soft colors |
| Monument | Refined frame | Centered symmetric row | Double-line or gold-line border, serif numerals, center-aligned |
| Terrain | Icon + number card | Bottom horizontal row | SVG data URI natural icons, slight floating feel |

### 3.4 Chart Configuration per Style

**Orbital:** radar, gauge (270-degree arc), donut. Bars use polar coordinate system. Circular tooltips.

**Mosaic:** bar (square, solid fill, no gradient), heatmap, treemap, sparklines. Step lines instead of curves. Minimal grid padding (left:32, right:8).

**Horizon:** horizontal bar, sankey, line (horizontal time axis emphasis). Area charts with horizontal gradient. Funnel left-to-right.

**Organic:** smooth curves, liquid fill, pictorialBar. `smooth: true` everywhere. Soft area fill gradient. Colors desaturated 15-20%.

**Monument:** classic bar (no gradient, no radius), precise line (no area fill, lineWidth 2, point markers), refined tables. Serif font. No decorative animation.

**Terrain:** map, heatmap, effectScatter (ripple). PictorialBar with natural element SVG paths. Natural color palettes.

### 3.5 Background Atmosphere

| Style | Background | Technique |
|-------|-----------|-----------|
| Orbital | Concentric circle radar grid + slow-rotating orbit arcs | Canvas concentric circles + CSS-animated SVG arcs |
| Mosaic | Solid or very subtle linear gradient | Single `background: linear-gradient(...)` |
| Horizon | Top-to-bottom gradient bands + horizontal contour lines | CSS gradient + `repeating-linear-gradient` horizontal lines |
| Organic | Soft color blobs (2-3 large blurred circles) + faint noise | Multiple `filter:blur(100px)` absolutely-positioned divs |
| Monument | Dark warm + very faint regular texture (diamond/lattice) | SVG pattern inlined in CSS at very low opacity |
| Terrain | Bottom ground-to-top sky vertical gradient + optional map base | CSS gradient + optional static map image base layer |

### 3.6 Motion Tokens

| Token | Orbital | Mosaic | Horizon | Organic | Monument | Terrain |
|-------|---------|--------|---------|---------|----------|---------|
| `--motion-duration` | 400ms | 150ms | 250ms | 500ms | 200ms | 350ms |
| `--motion-easing` | ease-in-out | cubic-bezier(0.4,0,0.2,1) | ease-out | cubic-bezier(0.25,0.1,0.25,1) | linear | ease-in-out |
| `--motion-ambient` | orbit rotation 8-15s | none | horizontal scan line 4s | breathing scale 6s | none | water ripple 5s |

> Card hover behavior is defined in Section 3.2 `--card-hover` token. Motion tokens here govern duration, easing, and ambient animations only.

---

## 4. File Structure Changes

### 4.1 New Directory: references/styles/

```
references/styles/
├── orbital.md
├── mosaic.md
├── horizon.md
├── organic.md
├── monument.md
└── terrain.md
```

Each style file (~200-300 lines) contains:
1. Layout skeleton (ASCII diagram + CSS implementation)
2. Component tokens (card, KPI, title CSS blocks)
3. Chart configuration (recommended chart types + ECharts option templates)
4. Background implementation (complete HTML+CSS)
5. Motion (ambient + hover/transition CSS)
6. Full CSS template (copy-ready :root + component styles)

### 4.2 Modified Files

**design-language.md** — Rewritten to:
- Introduce style matrix concept and token application mechanism
- Keep base-layer tokens (spacing scale, typography, naming conventions)
- Reference style files for per-style tokens
- Retain industry color override table (as overrides, not definitions)

**patterns-catalog.md** — Rewritten to:
- Add industry-to-style mapping table as primary content
- Add style selection guide for custom industries (decision tree based on business characteristics)
- Update read order: catalog → style → industry → compile-rules
- Slim down industry descriptions to data semantics only

**industries/*.md** — Slimmed to:
- Industry name + mapped style
- Core KPI list (name, unit, recommended widget type)
- Color overrides (accent-primary, accent-secondary, bg-app only)
- Widget recommendations (chart types, arrangement suggestions)
- AI trigger keywords
- Remove: visual signature, layout skeleton, card styles, background effects

**compile-rules.md** — Minor update:
- STEP 2 adds: resolve style from mapping table, read `references/styles/<style>.md`
- Extract visual tokens from style file + color overrides from industry file

**SKILL.md** — Updated reference layout read order

### 4.3 Unchanged Files

- `data-contract.md` — JSON schema, polling protocol, postMessage
- `scripts/validate.py` — Validation rules
- `scripts/preview.py` — Preview script
- CSP security policy, widget SQL rules, all security constraints

---

## 5. Edge Cases

**Custom industry (not in 12 presets):** patterns-catalog.md includes a "style selection guide" — a decision tree based on business characteristics:
- Data hub / monitoring / 360-degree awareness → Orbital
- Dense metrics / KPI-driven / ops cockpit → Mosaic
- Process flow / pipeline / sequential stages → Horizon
- People / care / learning / warmth → Organic
- Money / regulation / authority / precision → Monument
- Nature / geography / spatial / environment → Terrain

AI matches style based on industry traits using this guide.

**Style override per dashboard:** dashboard.json `theme` field extended to `{ "industry": "ecommerce", "style": "organic" }`. The `style` field is optional — omitting it uses the default mapping. When style is overridden, industry colors apply as **recommendations** rather than hard requirements. If the industry palette visually conflicts with the style (e.g., Agriculture earth tones on Monument navy/gold), the style file's default palette takes precedence. The merge rule: industry colors win only when they pass a minimum contrast ratio of 3:1 against the style's card background; otherwise the style's own palette is used.

**No style mixing within a single dashboard:** Each dashboard uses exactly one style to maintain visual coherence. Multi-style experiences (e.g., overview page uses Mosaic, detail page uses Monument) are handled at the dashboard group level — separate dashboard.json files, each with its own style. Single-page mixing is not supported.

---

## 6. Cross-Cutting Concerns

### 6.1 Responsive Design

All dashboards target large-screen display (1920x1080 minimum). Mobile/small-screen adaptation is out of scope for this spec — the bezel skill produces big-screen dashboards for TV-wall and monitor display. If a future spec addresses responsive layouts, each style would define its own breakpoint strategy.

### 6.2 Accessibility

Each style file must include an accessibility checklist:

- **Color contrast:** All text must meet WCAG AA (4.5:1 for body text, 3:1 for large text) against its card background. This is especially important for Organic (low bg opacity + blur) and Mosaic (high bg opacity, potentially low contrast for muted labels). The style file must specify minimum text colors that achieve this.
- **Font fallback:** Monument's serif font (Noto Serif SC via CDN) must include `'PingFang SC', 'Microsoft YaHei', serif` as fallback chain.
- **prefers-reduced-motion:** All ambient animations and hover transitions must be wrapped in a `@media (prefers-reduced-motion: no-preference)` query. When the user prefers reduced motion, animations are suppressed and only instant state changes remain. This applies to all 6 styles.
- **Focus visible:** Interactive elements (if any) must have visible focus indicators. For most dashboards this is minimal since they are display-only, but filter widgets or drill-down elements need `:focus-visible` outlines.

### 6.3 Performance

Background effects are the primary performance concern:

- **Organic** (2-3 large `filter:blur(100px)` divs): `filter:blur()` on large elements can cause frame drops on low-end GPUs. Mitigation: use `will-change: transform` and limit blur radius to 80px on devices that report low GPU capability (checked via `navigator.hardwareConcurrency` heuristic), or disable blur entirely via `prefers-reduced-motion`.
- **Orbital** (Canvas concentric circles): Canvas rendering is lightweight. Use `requestAnimationFrame` for the rotation animation and cap frame rate at 30fps for the background layer (it's slow-moving, 60fps is unnecessary).
- **General rule:** Background effect canvases use `{ alpha: false }` option and skip clearing when no visible change has occurred.

### 6.4 Token Consistency

6 styles × 20+ tokens = 120+ design decisions. To maintain consistency:

- Each style file includes a self-check section listing all tokens with their values in a single table, making it easy to audit.
- `scripts/validate.py` is extended in Phase 3 to check for token completeness: every compiled HTML must define all required `--card-*` and `--motion-*` CSS custom properties on `:root`. Missing tokens cause a validation warning (not error, since fallback values exist in the base design-language.md).

---

## 7. Implementation Phases

**Phase 1 — Infrastructure:**
- Create `references/styles/` directory with 6 style file skeletons
- Rewrite `design-language.md` with style layer concept and token application mechanism
- Update `patterns-catalog.md` with mapping table and style selection guide

**Phase 2 — Style File Population:**
- Write complete specs for 3 most-different styles first (Orbital, Mosaic, Organic)
- Validate design system coherence with these 3

**Phase 3 — Full Coverage:**
- Complete all 6 style files (each with accessibility checklist and performance notes)
- Slim down 12 industry files
- Update compile-rules.md (STEP 2 style resolution)
- Extend validate.py with token completeness check

**Phase 4 — Template Update:**
- Rewrite 12 HTML templates per new style system
- Run validate.py for compliance
- Visual regression comparison: screenshot each new template alongside the old version, review for intended style differentiation
