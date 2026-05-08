import type { LayoutEngine, ContainerSize, RenderedPosition, ValidationResult } from './layout-engine'
import type { Widget, GridLayout, GridPosition } from '../schema'

const DEFAULTS: Record<string, { w: number; h: number }> = {
  chart: { w: 6, h: 4 },
  kpi: { w: 3, h: 2 },
  table: { w: 12, h: 6 },
  markdown: { w: 12, h: 3 },
  filter: { w: 3, h: 1 },
  section: { w: 12, h: 1 },
  divider: { w: 12, h: 1 },
  image: { w: 4, h: 4 },
}

function rectsOverlap(a: GridPosition, b: GridPosition): boolean {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y
}

export class GridLayoutEngine implements LayoutEngine<GridLayout> {
  kind = 'grid'

  validate(_layout: GridLayout, widgets: Widget[]): ValidationResult {
    const errors: ValidationResult['errors'] = []
    for (let i = 0; i < widgets.length; i++) {
      for (let j = i + 1; j < widgets.length; j++) {
        if (rectsOverlap(widgets[i].position, widgets[j].position)) {
          errors.push({
            path: `/widgets/${i}/position`,
            message: `widgets[${i}] (${widgets[i].id}) overlaps with widgets[${j}] (${widgets[j].id})`,
          })
        }
      }
    }
    return { errors }
  }

  pack(widgets: Widget[], container: ContainerSize): RenderedPosition[] {
    // Placeholder: linear layout left-to-right, top-to-bottom
    const result: RenderedPosition[] = []
    let cursorX = 0
    let cursorY = 0
    let rowHeight = 0
    for (const w of widgets) {
      const cellW = w.position.w
      const cellH = w.position.h
      if (cursorX + cellW > 12) {
        cursorX = 0
        cursorY += rowHeight
        rowHeight = 0
      }
      result.push({
        widgetId: w.id,
        rect: {
          left: cursorX * (container.width / 12),
          top: cursorY * 32,
          width: cellW * (container.width / 12),
          height: cellH * 32,
          zIndex: w.position.z ?? 0,
        },
      })
      rowHeight = Math.max(rowHeight, cellH)
      cursorX += cellW
    }
    return result
  }

  defaultPosition(widgetType: Widget['type']): GridPosition {
    const d = DEFAULTS[widgetType] ?? { w: 4, h: 4 }
    return { x: 0, y: 0, w: d.w, h: d.h }
  }

  autoPackPosition(size: { w: number; h: number }, existing: Widget[]): { x: number; y: number } {
    // Build a simple row-major occupancy grid approach:
    // Find the first position (scanning left-to-right, top-to-bottom) that fits.
    const occupied = (yStart: number, xStart: number, w: number, h: number): boolean => {
      for (const wgt of existing) {
        const p = wgt.position
        if (xStart < p.x + p.w && xStart + w > p.x && yStart < p.y + p.h && yStart + h > p.y) {
          return true
        }
      }
      return false
    }

    for (let y = 0; y < 200; y++) {
      for (let x = 0; x <= 12 - size.w; x++) {
        if (!occupied(y, x, size.w, size.h)) {
          return { x, y }
        }
      }
    }
    return { x: 0, y: 200 }
  }
}
