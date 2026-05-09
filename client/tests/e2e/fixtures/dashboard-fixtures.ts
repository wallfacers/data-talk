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
 * Uses a single oversized markdown widget to push the serialized JSON
 * past 256 * 1024 bytes.
 */
export function makeOversizedDashboardPayload(): Dashboard {
  // 256 KB = 262144 bytes.  Build a markdown string ~300 KB to safely exceed.
  const bigText = 'A'.repeat(300 * 1024)

  return makeDashboardPayload({
    id: `dash_e2e_oversized_${Date.now()}`,
    title: 'Oversized E2E Dashboard',
    widgets: [
      {
        id: 'markdown_w_oversized',
        type: 'markdown',
        position: { x: 0, y: 0, w: 12, h: 4 },
        options: { text: bigText },
      },
    ],
  })
}
