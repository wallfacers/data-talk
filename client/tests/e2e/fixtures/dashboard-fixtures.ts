/**
 * Dashboard payload factories for E2E contract tests.
 *
 * Produces well-formed dashboard JSON that matches the adapter's
 * DashboardArtifactService schema (schemaVersion 1, 12-col grid, etc.).
 */

import type { Dashboard } from '../../../src/features/dashboard/schema'

/**
 * Build a minimal valid dashboard payload.
 * Pass `overrides` to replace any top-level field.
 */
export function makeDashboardPayload(overrides: Partial<Dashboard> = {}): Dashboard {
  return {
    schemaVersion: 1,
    id: `dash_e2e_${Date.now()}`,
    title: 'E2E Dashboard',
    description: 'Dashboard created by Playwright contract tests',
    defaultConnectionId: null,
    parameters: [],
    widgets: [
      {
        id: 'chart_w_e2e1',
        type: 'chart',
        position: { x: 0, y: 0, w: 6, h: 4 },
        query: { sql: 'SELECT 1 AS value', paramRefs: {} },
        options: {
          title: 'Metric',
          echartsOption: { xAxis: { type: 'category' }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: [1] }] },
          dataMapping: { rowsAsDataset: true },
        },
      },
      {
        id: 'markdown_w_e2e1',
        type: 'markdown',
        position: { x: 6, y: 0, w: 6, h: 3 },
        options: { text: '## Notes' },
      },
    ],
    layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
    version: 1,
    createdAt: 0,
    updatedAt: 0,
    ...overrides,
  } as Dashboard
}

/**
 * Build a dashboard payload that exceeds the 256 KB server limit.
 * Uses many small widgets with padding data to push the serialized JSON
 * past 256 * 1024 bytes while keeping each widget individually valid.
 */
export function makeOversizedDashboardPayload(): Dashboard {
  // Build ~20 widgets each ~15 KB of padding => ~300 KB total, well past 256 KB.
  const widgets: Dashboard['widgets'] = Array.from({ length: 20 }, (_, i) => ({
    id: `markdown_w_ovrsz${String(i).padStart(4, '0')}`,
    type: 'markdown' as const,
    position: { x: (i % 4) * 3, y: Math.floor(i / 4) * 3, w: 3, h: 3 },
    options: { text: `## Block ${i}\n` + 'X'.repeat(14 * 1024) },
  }))

  return makeDashboardPayload({
    id: `dash_e2e_oversized_${Date.now()}`,
    title: 'Oversized E2E Dashboard',
    widgets,
  })
}
