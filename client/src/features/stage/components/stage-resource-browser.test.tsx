import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Connection } from '@/services/api/connection'
import { StageResourceBrowser } from './stage-resource-browser'

const connections: Connection[] = [
  {
    id: 'conn-a',
    name: 'orders-prod',
    kind: 'postgres',
    host: 'localhost',
    port: 5432,
    databaseName: 'orders',
    username: 'demo',
    createdAt: 1,
    connectTimeout: 3000,
    lastTestStatus: null,
    lastTestAt: null,
  },
]

describe('StageResourceBrowser (shim)', () => {
  it('renders nothing while compatibility shim is retained', () => {
    const { container } = render(
      <StageResourceBrowser
        sessionId="sess-1"
        connections={connections}
        expandedNodeIds={[]}
        selection={null}
        onSelectionChange={() => {}}
        onExpandedChange={() => {}}
        onToolAction={() => {}}
      />,
    )

    expect(container.firstChild).toBeNull()
  })
})
