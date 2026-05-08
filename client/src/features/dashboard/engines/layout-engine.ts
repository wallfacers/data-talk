import type { Widget, GridLayout, GridPosition } from '../schema'

export interface ContainerSize { width: number; height: number }
export interface RenderedPosition {
  widgetId: string
  rect: { left: number; top: number; width: number; height: number; zIndex: number }
}
export interface ValidationResult { errors: { path: string; message: string }[] }

export interface LayoutEngine<L = GridLayout> {
  kind: string
  validate(layout: L, widgets: Widget[]): ValidationResult
  pack(widgets: Widget[], container: ContainerSize): RenderedPosition[]
  defaultPosition(widgetType: Widget['type']): GridPosition
  autoPackPosition(size: { w: number; h: number }, existing: Widget[]): { x: number; y: number }
}
