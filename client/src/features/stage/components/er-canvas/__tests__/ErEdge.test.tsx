import { render } from '@testing-library/react'
import { ReactFlowProvider, Position } from '@xyflow/react'
import { describe, expect, it } from 'vitest'

import { ErEdge } from '../ErEdge'

const baseProps = {
  id: 'fk:orders.user_id->users.id',
  source: 'orders',
  target: 'users',
  sourceX: 0,
  sourceY: 0,
  targetX: 200,
  targetY: 0,
  sourcePosition: Position.Right,
  targetPosition: Position.Left,
  data: {
    kind: 'fk' as const,
    fromColumn: 'user_id',
    toColumn: 'id',
    relationType: 'many_to_one',
  },
  style: {},
  selected: false,
  markerEnd: undefined,
  markerStart: undefined,
  interactionWidth: 20,
  pathOptions: undefined,
}

describe('<ErEdge>', () => {
  it('renders an SVG path for a normal smoothstep edge', () => {
    const { container } = render(
      <ReactFlowProvider>
        <svg>
          <ErEdge {...baseProps} />
        </svg>
      </ReactFlowProvider>,
    )

    const path = container.querySelector('path')
    expect(path).toBeTruthy()
    expect(path?.getAttribute('d')).toContain('M')
  })

  it('renders a virtual relation as dashed', () => {
    const { container } = render(
      <ReactFlowProvider>
        <svg>
          <ErEdge {...baseProps} data={{ ...baseProps.data, kind: 'virtual' }} />
        </svg>
      </ReactFlowProvider>,
    )

    expect(container.querySelector('path[stroke-dasharray]')).toBeTruthy()
  })

  it('uses self-ref loopback when source equals target', () => {
    const { container } = render(
      <ReactFlowProvider>
        <svg>
          <ErEdge {...baseProps} id="self" source="orders" target="orders" targetX={80} targetY={60} />
        </svg>
      </ReactFlowProvider>,
    )

    const d = container.querySelector('path')?.getAttribute('d') ?? ''
    expect(d.match(/L /g)?.length ?? 0).toBeGreaterThanOrEqual(5)
  })
})
