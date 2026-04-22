import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StageToolRow } from './stage-tool-row'

describe('StageToolRow (shim)', () => {
  it('renders nothing while compatibility shim is retained', () => {
    const { container } = render(<StageToolRow sessionId="sess-1" />)
    expect(container.firstChild).toBeNull()
  })
})
