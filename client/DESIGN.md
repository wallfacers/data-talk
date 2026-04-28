---
version: alpha
name: DataTalk Client
description: Contract source of truth for the DataTalk AI-native data research workbench.
product: AI-native data research workbench
themes:
  strategy: dual-theme
  light: "Calm, paper-like research surface"
  dark: "Crisp, instrument-panel research surface"
primitives:
  neutral:
    0: "#FFFFFF"
    25: "#FCFDFE"
    50: "#F8FAFC"
    100: "#F1F5F9"
    200: "#E2E8F0"
    300: "#CBD5E1"
    400: "#94A3B8"
    500: "#64748B"
    600: "#475569"
    700: "#334155"
    800: "#1E293B"
    900: "#0F172A"
    950: "#020617"
  cobalt:
    50: "#EFF6FF"
    100: "#DBEAFE"
    200: "#BFDBFE"
    300: "#93C5FD"
    400: "#60A5FA"
    500: "#3B82F6"
    600: "#2563EB"
    700: "#1D4ED8"
    800: "#1E40AF"
    900: "#1E3A8A"
  amber:
    50: "#FFFBEB"
    100: "#FEF3C7"
    200: "#FDE68A"
    300: "#FCD34D"
    400: "#FBBF24"
    500: "#F59E0B"
    600: "#D97706"
    700: "#B45309"
    800: "#92400E"
  green:
    100: "#DCFCE7"
    500: "#22C55E"
    700: "#15803D"
  red:
    100: "#FEE2E2"
    500: "#EF4444"
    700: "#B91C1C"
  sky:
    100: "#E0F2FE"
    500: "#0EA5E9"
    700: "#0369A1"
semantic:
  light:
    bg:
      app: "neutral.25"
      canvas: "neutral.0"
      panel: "neutral.0"
      subtle: "neutral.50"
      elevated: "neutral.0"
      overlay: "rgba(15, 23, 42, 0.40)"
    text:
      strong: "neutral.900"
      base: "neutral.800"
      muted: "neutral.600"
      soft: "neutral.500"
      inverse: "neutral.0"
    border:
      subtle: "neutral.200"
      default: "neutral.300"
      strong: "neutral.400"
    accent:
      primary: "cobalt.700"
      primaryHover: "cobalt.800"
      primarySurface: "cobalt.50"
      primaryBorder: "cobalt.200"
      warn: "amber.500"
      warnSurface: "amber.50"
    status:
      success: "green.500"
      successSurface: "green.100"
      warning: "amber.500"
      warningSurface: "amber.50"
      danger: "red.500"
      dangerSurface: "red.100"
      info: "sky.500"
      infoSurface: "sky.100"
  dark:
    bg:
      app: "neutral.950"
      canvas: "neutral.900"
      panel: "neutral.900"
      subtle: "neutral.800"
      elevated: "neutral.800"
      overlay: "rgba(2, 6, 23, 0.72)"
    text:
      strong: "neutral.25"
      base: "neutral.100"
      muted: "neutral.400"
      soft: "neutral.500"
      inverse: "neutral.950"
    border:
      subtle: "rgba(255, 255, 255, 0.08)"
      default: "rgba(255, 255, 255, 0.12)"
      strong: "rgba(255, 255, 255, 0.18)"
    accent:
      primary: "cobalt.400"
      primaryHover: "cobalt.300"
      primarySurface: "rgba(37, 99, 235, 0.18)"
      primaryBorder: "rgba(96, 165, 250, 0.32)"
      warn: "amber.400"
      warnSurface: "rgba(245, 158, 11, 0.14)"
    status:
      success: "green.500"
      successSurface: "rgba(34, 197, 94, 0.14)"
      warning: "amber.400"
      warningSurface: "rgba(245, 158, 11, 0.14)"
      danger: "red.500"
      dangerSurface: "rgba(239, 68, 68, 0.16)"
      info: "sky.500"
      infoSurface: "rgba(14, 165, 233, 0.16)"
interaction:
  focusRing:
    light: "rgba(37, 99, 235, 0.35)"
    dark: "rgba(96, 165, 250, 0.38)"
  hover:
    light: "rgba(15, 23, 42, 0.04)"
    dark: "rgba(255, 255, 255, 0.06)"
  active:
    light: "rgba(15, 23, 42, 0.08)"
    dark: "rgba(255, 255, 255, 0.10)"
  selected:
    light: "cobalt.50"
    dark: "rgba(37, 99, 235, 0.18)"
  disabled:
    light: "rgba(15, 23, 42, 0.38)"
    dark: "rgba(255, 255, 255, 0.34)"
typography:
  ui-xs:
    fontFamily: "'Source Sans 3', 'Noto Sans SC', 'PingFang SC', sans-serif"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: "16px"
  ui-sm:
    fontFamily: "'Source Sans 3', 'Noto Sans SC', 'PingFang SC', sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: "18px"
  ui-md:
    fontFamily: "'Source Sans 3', 'Noto Sans SC', 'PingFang SC', sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
  ui-lg:
    fontFamily: "'Source Sans 3', 'Noto Sans SC', 'PingFang SC', sans-serif"
    fontSize: "16px"
    fontWeight: 500
    lineHeight: "24px"
  ui-xl:
    fontFamily: "'Source Sans 3', 'Noto Sans SC', 'PingFang SC', sans-serif"
    fontSize: "20px"
    fontWeight: 600
    lineHeight: "28px"
  ui-2xl:
    fontFamily: "'Source Sans 3', 'Noto Sans SC', 'PingFang SC', sans-serif"
    fontSize: "24px"
    fontWeight: 600
    lineHeight: "32px"
  mono-sm:
    fontFamily: "'JetBrains Mono', 'SFMono-Regular', 'Cascadia Mono', monospace"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: "18px"
  mono-md:
    fontFamily: "'JetBrains Mono', 'SFMono-Regular', 'Cascadia Mono', monospace"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
spacing:
  1: "4px"
  2: "8px"
  3: "12px"
  4: "16px"
  5: "20px"
  6: "24px"
  8: "32px"
  10: "40px"
  12: "48px"
radius:
  sm: "8px"
  md: "10px"
  lg: "14px"
  xl: "20px"
  2xl: "24px"
  3xl: "28px"
  4xl: "32px"
density:
  compact: "sidebar, table, toolbar, tabs, metadata, settings navigation"
  comfortable: "chat messages, composer, dialog body, empty state"
  focused: "hero, critical confirmation, first-run guidance"
motion:
  fast: "120ms"
  normal: "180ms"
  slow: "240ms"
  easing:
    standard: "cubic-bezier(0.2, 0, 0, 1)"
    enter: "cubic-bezier(0.16, 1, 0.3, 1)"
    exit: "cubic-bezier(0.3, 0, 0.8, 0.15)"
components:
  sidebar:
    bg: "bg.subtle"
    border: "border.subtle"
    activeBg: "interaction.selected"
    activeText: "text.strong"
  composer:
    bg: "bg.panel"
    border: "border.default"
    focus: "interaction.focusRing"
  message:
    userSurface: "bg.subtle"
    assistantSurface: "bg.canvas"
    toolSurface: "bg.panel"
    errorSurface: "status.dangerSurface"
    errorBorder: "status.danger"
  stage:
    chrome: "bg.subtle"
    surface: "bg.canvas"
    tabIdle: "text.muted"
    tabActive: "accent.primary"
    railBg: "bg.panel"
  table:
    headerBg: "bg.subtle"
    rowHover: "interaction.hover"
    rowSelected: "interaction.selected"
  chart:
    focus: "accent.primary"
    compare: "accent.warn"
    grid: "border.subtle"
---

## Overview

DataTalk is an AI-native data research workbench: a collaborative instrument for asking, inspecting, and operating on data.

The client design contract is the source of truth for how Chat and Workbench coexist as one system. New UI work must map to the semantic vocabulary here before adding implementation detail.

Typography families in this contract are the target design tokens; the current runtime still inherits the existing app font variables until a later font-loading pass.

## Principles

- Precision First: structure, density, and boundary do the visual work before decoration.
- Dual-Core, One System: Chat and Workbench are peer surfaces with one token system.
- Neutral Backbone, Focused Signal: neutrals carry most of the interface; cobalt carries focus, selection, and primary action.
- Calm in Light, Crisp in Dark: both themes must support long sessions without changing semantics.
- Dense but Breathable: desktop-grade information density without crowding.
- Motion as Confirmation: motion confirms state change; it does not decorate.

## Theme Semantics

DataTalk ships light and dark as equal first-class themes. Components do not invent theme-specific behavior; they consume semantic tokens and inherit the current theme mapping.

- `bg.app` is the application frame.
- `bg.canvas` is the main reading or work surface.
- `bg.panel` is the default contained surface for controls and focused work areas.
- `bg.subtle` is the low-emphasis navigation or grouping surface.
- `text.strong` and `text.base` carry primary reading flow.
- `text.muted` and `text.soft` carry metadata, chrome, and low-priority detail.
- `accent.primary` is reserved for current object, primary action, and selected emphasis.
- `status.*` is reserved for health, warning, danger, and information semantics.

Interaction values are stable across components:

- `focusRing` for keyboard and focus-visible affordances.
- `hover` for low-emphasis pointing feedback.
- `active` for pressed or engaged control state.
- `selected` for current item, current tab, or selected row.
- `disabled` for unavailable controls; disabled state must not rely on color alone.

## Layout Modes

DataTalk is not a single-column chat app and not a monolithic SQL IDE. The client uses a stable three-layer structure:

1. `navigation skeleton`: Sidebar
2. `conversation lane`: Session chat and collaborative narrative
3. `instrument lane`: Stage, SQL, artifacts, and structured work surfaces

Primary page modes:

- `Conversation Workspace`
- `Instrument Panel`
- `Resource Management`
- `Structured Form`
- `Focused Modal Surface`

These modes share the same tokens, typography, and interaction rules. Differences come from density and composition, not from separate visual languages.

## Component Rules

The YAML `components` block below is the approved DataTalk alias contract (`bg`, `activeBg`, `rowSelected`, and similar keys). The current `designmd` linter does not model those product-specific alias sub-keys, so zero lint errors is required while component-level warnings are expected until the schema catches up.

- Sidebar uses `bg.subtle`, `border.subtle`, `interaction.selected`, and `text.strong`. It is navigation skeleton, not the main stage.
- Composer uses `bg.panel`, `border.default`, and `interaction.focusRing`. It is a composed work control, not a plain textarea shell.
- Messages distinguish user, assistant, tool, and error surfaces with semantics instead of saturated bubbles.
- Stage uses `bg.subtle` for chrome and `bg.canvas` for the main work surface. Tabs, rail, running state, and selection must be readable at a glance.
- Stage state is **global, not per-session**: tab list, workset, open / maximized, sidebar selection, and active rail panel are single values shared across all chat sessions. Switching session must not visually change the workbench. `StageTab` records carry no `scope` field on the instance; type-level scope is metadata in `tab-type-registry`.
- Tables use stable header hierarchy, light hover, explicit selected state, and mono treatment for numeric or technical content.
- Charts use neutral context with semantic color emphasis: focus object, compare object, and status objects each have one role.

## Data Visualization

Charts and tables must read as the same system.

- Brand cobalt marks the current focus series or current object.
- Amber marks compare or warning context.
- Green and red are for outcome or health semantics only.
- Neutral tones carry grid, axis, historical context, and background series.
- A single chart should have one dominant emphasis target.

## Accessibility

- Body text contrast must meet or exceed `4.5:1`.
- Large text and key icon contrast must meet or exceed `3:1`.
- Focus rings must remain visible in both themes.
- State cannot be communicated by color alone.
- Keyboard access must cover sidebar, tabs, dialogs, menus, and composer.
- `prefers-reduced-motion` must disable non-essential movement.
- Icon-only actions require accessible names.

## Do / Don't

### Do

- Map new UI to semantic tokens before inventing component-local color values.
- Keep Chat and Workbench visually related, even when density differs.
- Use border, background, and spacing to establish hierarchy.
- Treat the contract in this file as the first stop before implementation.

### Don't

- Do not use raw primitive colors directly in feature code.
- Do not split Chat and Workbench into unrelated visual systems.
- Do not overuse accent color outside focus, selection, and primary action.
- Do not use glassmorphism, cyberpunk color noise, or decorative animation as the base style.
