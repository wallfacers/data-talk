import { execSync } from 'child_process'
import * as fs from 'fs'

const DB_PATH = '/home/wushengzhou/workspace/github/data-talk/server/data-talk-adapter/data/datatalk.db'
const PLUGIN_PATH = '/home/wushengzhou/.data-talk/opencode/plugins/datatalk-mcp-context.js'

function sqlite3(sql: string): string {
  // PRAGMA busy_timeout=5000 tells sqlite3 CLI to block up to 5 s when the
  // database is locked instead of immediately returning SQLITE_BUSY.
  const command = `sqlite3 "${DB_PATH}" "PRAGMA busy_timeout=5000; ${sql}"`
  return execSync(command, { encoding: 'utf-8', timeout: 10000 }).trim()
}

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
  try {
    const result = sqlite3(
      'SELECT id, opencode_sid FROM sessions WHERE opencode_sid IS NOT NULL ORDER BY updated_at DESC LIMIT 1;'
    )
    if (!result) return null
    const parts = result.split('|')
    if (parts.length < 2 || !parts[1]) return null
    return { dataTalkSessionId: parts[0], openCodeSessionId: parts[1] }
  } catch {
    return null
  }
}

export function getLatestOpenCodeSessionId(): string | null {
  try {
    const result = sqlite3(
      'SELECT id FROM sessions WHERE opencode_sid IS NOT NULL ORDER BY updated_at DESC LIMIT 1;'
    )
    return result || null
  } catch {
    return null
  }
}

export function getOpenCodeSessionFor(dataTalkSessionId: string): string | null {
  try {
    const result = sqlite3(
      `SELECT opencode_sid FROM sessions WHERE id = '${dataTalkSessionId}' AND opencode_sid IS NOT NULL LIMIT 1;`
    )
    return result || null
  } catch {
    return null
  }
}

export function getOpenCodeSessionForConnection(connectionId: string): { dataTalkSessionId: string; openCodeSessionId: string } | null {
  try {
    const result = sqlite3(
      `SELECT id, opencode_sid FROM sessions WHERE connection_id = '${connectionId}' AND opencode_sid IS NOT NULL ORDER BY updated_at DESC LIMIT 1;`
    )
    if (!result) return null
    const parts = result.split('|')
    if (parts.length < 2 || !parts[1]) return null
    return { dataTalkSessionId: parts[0], openCodeSessionId: parts[1] }
  } catch {
    return null
  }
}
