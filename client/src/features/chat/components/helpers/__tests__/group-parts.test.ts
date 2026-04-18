import { describe, it, expect } from 'vitest'
import { groupParts } from '../group-parts'

const meta = { id: '', sessionID: 's', messageID: 'm', metadata: {} }
const toolPart = (id: string, tool: string, category?: string) => ({
  ...meta, id, type: 'tool', tool,
  state: { status: 'completed', input: {} },
  __descriptorCategory: category,
}) as any
const textPart = (id: string, text: string) => ({ ...meta, id, type: 'text', text }) as any

describe('groupParts', () => {
  it('merges consecutive metadata tools', () => {
    const parts = [
      toolPart('t1', 'describe_table', 'metadata'),
      toolPart('t2', 'list_tables', 'metadata'),
      textPart('x', 'hi'),
      toolPart('t3', 'describe_table', 'metadata'),
    ]
    const groups = groupParts(parts, (p: any) => p.__descriptorCategory === 'metadata')
    expect(groups.length).toBe(3)
    expect(groups[0].type).toBe('context-group')
    expect((groups[0] as any).refs.length).toBe(2)
    expect(groups[1].type).toBe('part')
    expect(groups[2].type).toBe('context-group')
  })
  it('empty array returns empty', () => {
    expect(groupParts([], () => false)).toEqual([])
  })
})
