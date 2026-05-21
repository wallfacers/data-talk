import { z } from 'zod'

const chartSemantics = z.object({
  chartType: z.enum(['bar', 'line', 'area', 'pie', 'funnel', 'scatter', 'radar', 'map']).optional(),
  colorScheme: z.enum(['warm', 'cool', 'monochrome', 'brand']).optional(),
  stacked: z.boolean().optional(),
  showLegend: z.boolean().optional(),
  showTooltip: z.boolean().optional(),
  showAreaFill: z.boolean().optional(),
  labelPosition: z.enum(['inside', 'outside', 'none']).optional(),
  gridGap: z.enum(['compact', 'normal', 'spacious']).optional(),
  rawEchartsOption: z.record(z.string(), z.unknown()).optional(),
})

const widgetQuery = z.object({
  connectionId: z.string().nullable().optional(),
  database: z.string().nullable().optional(),
  schema: z.string().nullable().optional(),
  sql: z.string(),
  paramRefs: z.record(z.string(), z.string()).default({}),
})

const parameterDef = z.object({
  id: z.string().regex(/^(global|local):[a-zA-Z0-9_:]+$/),
  scope: z.enum(['global', 'local']),
  ownerWidgetId: z.string().nullable().optional(),
  name: z.string(),
  type: z.enum(['date', 'date_range', 'string', 'number', 'string_list']),
  default: z.unknown(),
})

const refreshPolicy = z.object({
  intervalMs: z.number().int().min(1000).optional(),
  strategy: z.enum(['DATA_ONLY', 'FULL_RERENDER']).optional(),
})

const dashboardRefresh = z.object({
  defaultIntervalMs: z.number().int().min(1000).default(10000),
  pauseOnHidden: z.boolean().default(true),
})

const widget = z.object({
  id: z.string().regex(/^[a-z]+_w_[a-zA-Z0-9_]{4,32}$/),
  type: z.enum(['chart', 'kpi', 'table', 'markdown', 'filter', 'section', 'divider', 'image']),
  slot: z.string().min(1),
  title: z.string().min(1).max(64),
  patternId: z.string().regex(/^[a-z0-9-]+\.[a-z0-9-]+$/),
  chartSemantics: chartSemantics.optional(),
  parameters: z.array(parameterDef).optional(),
  query: widgetQuery.optional(),
  refresh: refreshPolicy.optional(),
  options: z.record(z.string(), z.unknown()),
})

const layoutV3 = z.object({
  engine: z.literal('free'),
  template: z.enum([
    'single-focus',
    'two-column-left-heavy',
    'two-column-right-heavy',
    'three-column-kpi-center',
    'top-kpi-bottom-charts',
    'grid-equal',
  ]),
  viewport: z.object({
    minWidth: z.number().int().min(640),
    aspect: z.string(),
  }).optional(),
})

export const dashboardSchema = z.object({
  schemaVersion: z.literal(3),
  id: z.string().regex(/^dash_[a-zA-Z0-9_]{4,}$/),
  title: z.string().min(1).max(256),
  description: z.string().max(32768).optional(),
  defaultConnectionId: z.string().nullable().optional(),
  defaultDatabase: z.string().nullable().optional(),
  defaultSchema: z.string().nullable().optional(),
  theme: z.string().regex(/^industry-[a-z-]+$/),
  renderer: z.literal('bezel'),
  refresh: dashboardRefresh.optional(),
  parameters: z.array(parameterDef),
  widgets: z.array(widget),
  layout: layoutV3,
  version: z.number().int().min(1),
  createdAt: z.number().int().min(0).default(() => Date.now()),
  updatedAt: z.number().int().min(0).default(() => Date.now()),
})

export type Dashboard = z.infer<typeof dashboardSchema>
export type Widget = z.infer<typeof widget>
export type WidgetQuery = z.infer<typeof widgetQuery>
export type ChartSemantics = z.infer<typeof chartSemantics>
export type LayoutV3 = z.infer<typeof layoutV3>
export type ParameterDef = z.infer<typeof parameterDef>
export type DashboardRefresh = z.infer<typeof dashboardRefresh>
export type WidgetRefresh = z.infer<typeof refreshPolicy>
