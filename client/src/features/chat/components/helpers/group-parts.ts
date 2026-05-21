import type { Part } from '@/services/channel/types'

export type PartGroup =
  | { type: 'part'; ref: Part }
  | { type: 'context-group'; refs: Part[]; key: string }

export type IsContextGroupTool = (part: Part) => boolean

export function groupParts(parts: Part[], isContextGroupTool: IsContextGroupTool): PartGroup[] {
  const result: PartGroup[] = []
  let group: Part[] = []

  const flush = () => {
    if (group.length > 0) {
      result.push({ type: 'context-group', refs: group, key: `ctx:${group[0].id}` })
      group = []
    }
  }

  for (const p of parts) {
    if (p.type === 'tool' && isContextGroupTool(p)) {
      group.push(p)
    } else {
      flush()
      result.push({ type: 'part', ref: p })
    }
  }
  flush()
  return result
}
