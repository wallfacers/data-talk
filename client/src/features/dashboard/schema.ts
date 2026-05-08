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

const chartOptions = z.object({
  title: z.string().optional(),
  echartsOption: z.record(z.string(), z.unknown()),
  dataMapping: z.object({ rowsAsDataset: z.literal(true) }),
  emphasis: z.enum(['cobalt', 'amber', 'neutral']).optional(),
})

const markdownOptions = z.object({
  text: z.string().max(32768),
  textAlign: z.enum(['left', 'center', 'right']).optional(),
})

const imageOptions = z.object({
  src: z.string().url().refine((u) => u.startsWith('https://'), 'image src must be https://'),
  alt: z.string().min(1),
  fit: z.enum(['cover', 'contain', 'fill']),
})

const widgetBase = z.object({
  id: z.string().regex(/^[a-z]+_w_[a-zA-Z0-9]{4,16}$/),
  position: gridPosition,
  parameters: z.array(parameterDef).optional(),
  query: widgetQuery.optional(),
})

const chartWidget = widgetBase.extend({
  type: z.literal('chart'),
  options: chartOptions,
})

const imageWidget = widgetBase.extend({
  type: z.literal('image'),
  options: imageOptions,
})

const markdownWidget = widgetBase.extend({
  type: z.literal('markdown'),
  options: markdownOptions,
})

const genericWidget = widgetBase.extend({
  type: z.enum(['kpi', 'table', 'filter', 'section', 'divider']),
  options: z.object({}).passthrough(),
})

const widget = z.union([chartWidget, imageWidget, markdownWidget, genericWidget])

const gridLayout = z.object({
  engine: z.literal('grid'),
  cols: z.literal(12),
  rowHeight: z.number().int().min(8).max(128),
  gap: z.number().int().min(0).max(32),
})

export const dashboardSchema = z.object({
  schemaVersion: z.literal(1),
  id: z.string().regex(/^dash_[a-zA-Z0-9_]{4,}$/),
  title: z.string().min(1).max(256),
  description: z.string().max(32768).optional(),
  defaultConnectionId: z.string().nullable().optional(),
  parameters: z.array(parameterDef),
  widgets: z.array(widget),
  layout: gridLayout,
  version: z.number().int().min(1),
  createdAt: z.number().int().min(0),
  updatedAt: z.number().int().min(0),
})

export type Dashboard = z.infer<typeof dashboardSchema>
export type Widget = z.infer<typeof widget>
export type WidgetQuery = z.infer<typeof widgetQuery>
export type GridPosition = z.infer<typeof gridPosition>
export type GridLayout = z.infer<typeof gridLayout>
export type ParameterDef = z.infer<typeof parameterDef>
