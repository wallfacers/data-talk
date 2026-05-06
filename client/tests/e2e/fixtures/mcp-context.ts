import { execSync } from 'child_process'
import * as fs from 'fs'

const DB_PATH = '/home/wushengzhou/workspace/github/data-talk/server/data-talk-adapter/data/datatalk.db'
const PLUGIN_PATH = '/home/wushengzhou/.data-talk/opencode/plugins/datatalk-mcp-context.js'

export function getBridgeNonce(): string {
  try {
    const content = fs.readFileSync(PLUGIN_PATH, 'utf-8')
    const match = content.match(/BRIDGE_NONCE = '([^']+)'/)
    return match?.[1] ?? ''
  } catch {
    return ''
  }
}

export function getLatestOpenCodeSession(): { dataTalkSessionId: string; openCodeSessionId: string } | null {
  for (let attempt = 0; attempt < 5; attempt++) {
    try {
      const result = execSync(
        `sqlite3 "${DB_PATH}" "SELECT id, opencode_sid FROM sessions WHERE opencode_sid IS NOT NULL ORDER BY updated_at DESC LIMIT 1;"`,
        { encoding: 'utf-8', timeout: 5000 }
      ).trim()
      if (!result) return null
      const parts = result.split('|')
      if (parts.length < 2 || !parts[1]) return null
      return { dataTalkSessionId: parts[0], openCodeSessionId: parts[1] }
    } catch (e: any) {
      if (attempt < 4 && e?.message?.includes('database is locked')) {
        // SQLite busy; brief backoff then retry
        const start = Date.now()
        while (Date.now() - start < 100) { /* spin */ }
        continue
      }
      return null
    }
  }
  return null
}

export function getOpenCodeSessionFor(dataTalkSessionId: string): string | null {
  try {
    const result = execSync(
      `sqlite3 "${DB_PATH}" "SELECT opencode_sid FROM sessions WHERE id = '${dataTalkSessionId}' AND opencode_sid IS NOT NULL LIMIT 1;"`,
      { encoding: 'utf-8', timeout: 5000 }
    ).trim()
    return result || null
  } catch {
    return null
  }
}

export function getOpenCodeSessionForConnection(connectionId: string): { dataTalkSessionId: string; openCodeSessionId: string } | null {
  try {
    const result = execSync(
      `sqlite3 "${DB_PATH}" "SELECT id, opencode_sid FROM sessions WHERE connection_id = '${connectionId}' AND opencode_sid IS NOT NULL ORDER BY updated_at DESC LIMIT 1;"`,
      { encoding: 'utf-8', timeout: 5000 }
    ).trim()
    if (!result) return null
    const parts = result.split('|')
    if (parts.length < 2 || !parts[1]) return null
    return { dataTalkSessionId: parts[0], openCodeSessionId: parts[1] }
  } catch {
    return null
  }
}
