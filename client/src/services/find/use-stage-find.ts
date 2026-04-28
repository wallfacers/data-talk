import { useEffect, useState } from 'react'
import type { StageTab } from '@/stores/stage-store'

const DEBOUNCE_MS = 200

/**
 * @deprecated 2026-04-28: The Phase 2 left rail uses in-memory filtering.
 * Server-backed FTS search will be re-introduced in a follow-up plan.
 */
export function useStageFind({ query, includeArchived }: { query: string; includeArchived: boolean }) {
  const [tabs, setTabs] = useState<StageTab[]>([])
  const [isLoading, setLoading] = useState(false)

  useEffect(() => {
    let cancelled = false
    const handle = setTimeout(async () => {
      setLoading(true)
      try {
        const body: Record<string, unknown> = {
          filter: { includeArchived },
          output: { mode: 'metadata', headLimit: 50, maxTabs: 50 },
        }
        if (query.trim()) {
          body.query = { mode: 'fts', pattern: query.trim(), caseInsensitive: true }
        }
        const r = await fetch('/api/stage/find', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify(body),
        })
        const j = await r.json()
        if (!cancelled) setTabs((j.items ?? []).map(toStageTab))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }, DEBOUNCE_MS)
    return () => { cancelled = true; clearTimeout(handle) }
  }, [query, includeArchived])

  return { tabs, isLoading }
}

function toStageTab(item: Record<string, unknown>): StageTab {
  return {
    tabId: (item.id as string) ?? '',
    type: (item.type as string) ?? 'unknown',
    title: (item.title as string) ?? '(untitled)',
    scope: (item.scope as 'workspace' | 'session') ?? 'workspace',
    connectionId: (item.connectionId as string) ?? undefined,
    database: (item.databaseName as string) ?? undefined,
    schema: (item.schemaName as string) ?? undefined,
    originSessionId: (item.originSessionId as string) ?? undefined,
    pinned: !!item.pinned,
    archived: !!item.archived,
    payloadVersion: Number(item.payloadVersion) || 1,
    createdAt: Number(item.createdAt) || 0,
    lastTouchedAt: Number(item.lastTouchedAt) || 0,
  } as StageTab
}
