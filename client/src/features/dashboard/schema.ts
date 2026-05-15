import { z } from 'zod'

const gridPosition = z.object({
  x: z.number().int().min(0).max(11),
  y: z.number().int().min(0),
  w: z.number().int().min(1).max(12),
  h: z.number().int().min(1),
  z: z.number().int().nullable().optional(),
})

const widgetQuery = z.object({
  connectionId: z.string().nullable().optional(),
  sql: z.string(),
  paramRefs: z.record(z.string(), z.string()),
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
  strategy: z.enum(['data-only', 'full-rerender']).optional(),
})

const dashboardRefresh = z.object({
  defaultIntervalMs: z.number().int().min(1000).default(10000),
  pauseOnHidden: z.boolean().default(true),
})

const widget = z.object({
  id: z.string().regex(/^[a-z]+_w_[a-zA-Z0-9_]{4,32}$/),
  type: z.enum(['chart', 'kpi', 'table', 'markdown', 'filter', 'section', 'divider', 'image']),
  patternId: z.string().regex(/^[a-z0-9-]+\.[a-z0-9-]+$/),
  position: gridPosition,
  parameters: z.array(parameterDef).optional(),
  query: widgetQuery.optional(),
  refresh: refreshPolicy.optional(),
  options: z.record(z.string(), z.unknown()),
})

const freeLayout = z.object({
  engine: z.literal('free'),
  viewport: z.object({
    minWidth: z.number().int().min(640),
    aspect: z.string().regex(/^\d+:\d+$/),
  }).optional(),
})

export const dashboardSchema = z.object({
  schemaVersion: z.literal(2),
  id: z.string().regex(/^dash_[a-zA-Z0-9_]{4,}$/),
  title: z.string().min(1).max(256),
  description: z.string().max(32768).optional(),
  defaultConnectionId: z.string().nullable().optional(),
  theme: z.string().regex(/^industry-[a-z-]+$/),
  renderer: z.literal('bezel'),
  refresh: dashboardRefresh.optional(),
  parameters: z.array(parameterDef),
  widgets: z.array(widget),
  layout: freeLayout,
  version: z.number().int().min(1),
  createdAt: z.number().int().min(0),
  updatedAt: z.number().int().min(0),
})

export type Dashboard = z.infer<typeof dashboardSchema>
export type Widget = z.infer<typeof widget>
export type WidgetQuery = z.infer<typeof widgetQuery>
export type GridPosition = z.infer<typeof gridPosition>
export type FreeLayout = z.infer<typeof freeLayout>
export type ParameterDef = z.infer<typeof parameterDef>
export type DashboardRefresh = z.infer<typeof dashboardRefresh>
export type WidgetRefresh = z.infer<typeof refreshPolicy>
