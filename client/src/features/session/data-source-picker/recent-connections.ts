import type { Connection } from '@/services/api/connection'

const STORAGE_KEY = 'data-talk.recent-connections'

export function readRecentConnectionIds(): string[] {
  if (typeof window === 'undefined') return []
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === 'string') : []
  } catch {
    return []
  }
}

export function writeRecentConnectionIds(nextIds: string[]) {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(nextIds.slice(0, 10)))
}

export function rememberRecentConnection(id: string) {
  const current = readRecentConnectionIds().filter((x) => x !== id)
  writeRecentConnectionIds([id, ...current])
}

export function rankConnections(connections: Connection[], recentIds: string[], query: string): Connection[] {
  const needle = query.trim().toLowerCase()
  const recentIndex = new Map(recentIds.map((id, index) => [id, index]))

  return connections
    .filter((connection) => {
      if (!needle) return true
      const haystack = [
        connection.name,
        connection.kind,
        connection.host,
        connection.databaseName ?? '',
      ].join(' ').toLowerCase()
      return haystack.includes(needle)
    })
    .sort((a, b) => {
      const aRecent = recentIndex.get(a.id)
      const bRecent = recentIndex.get(b.id)
      if (aRecent !== undefined && bRecent !== undefined) return aRecent - bRecent
      if (aRecent !== undefined) return -1
      if (bRecent !== undefined) return 1
      return a.name.localeCompare(b.name)
    })
}
