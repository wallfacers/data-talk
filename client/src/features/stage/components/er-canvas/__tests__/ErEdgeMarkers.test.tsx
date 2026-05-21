import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ErEdgeMarkers } from '../ErEdgeMarkers'

describe('<ErEdgeMarkers>', () => {
  it('defines semantic arrow markers for ER edges', () => {
    const { container } = render(<ErEdgeMarkers />)
    const root = container.querySelector('svg')

    expect(root).toHaveAttribute('width', '0')
    expect(root).toHaveAttribute('height', '0')
    expect(container.querySelector('#er-edge-arrow-default')).toBeInTheDocument()
    expect(container.querySelector('#er-edge-arrow-virtual')).toBeInTheDocument()
    expect(container.querySelector('#er-edge-arrow-selected')).toBeInTheDocument()
    expect(container.innerHTML).toContain('var(--dt-border-strong)')
    expect(container.innerHTML).toContain('var(--dt-accent-warn)')
    expect(container.innerHTML).toContain('var(--dt-accent-primary)')
    expect(container.innerHTML).not.toMatch(/#[0-9a-f]{3,8}\b/i)
  })
})
