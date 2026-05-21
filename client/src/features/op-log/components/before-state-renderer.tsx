import { useMemo } from 'react'
import { BeforeStateJsonTree } from './before-state-json-tree'
import { BeforeStateGrid } from './before-state-grid'
import { BeforeStateDiff } from './before-state-diff'

interface BeforeStateRendererProps {
  beforeStateJson: string
  operation: string
}

export function BeforeStateRenderer({ beforeStateJson, operation }: BeforeStateRendererProps) {
  const parsed = useMemo(() => {
    try {
      return JSON.parse(beforeStateJson) as Record<string, unknown>[]
    } catch {
      return null
    }
  }, [beforeStateJson])

  if (!parsed || !Array.isArray(parsed)) {
    return <pre className="font-mono text-[13px] text-text-muted">{beforeStateJson}</pre>
  }

  // UPDATE: use diff view
  if (operation === 'UPDATE') {
    return <BeforeStateDiff beforeState={parsed} />
  }

  const rowCount = parsed.length
  const colCount = parsed.length > 0 ? Object.keys(parsed[0]).length : 0

  // Small data: JSON tree
  if (rowCount <= 5 && colCount <= 8) {
    return <BeforeStateJsonTree data={parsed} />
  }

  // Large data: grid
  return <BeforeStateGrid rows={parsed} />
}
