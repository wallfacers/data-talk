import { execSync } from 'child_process'
import * as fs from 'fs'
import * as os from 'os'
import * as path from 'path'

const DB_PATH = process.env.DATATALK_SQLITE_PATH ?? '/home/wallfacers/project/data-talk/server/data-talk-adapter/data/datatalk.db'
const PLUGIN_PATH = process.env.DATATALK_PLUGIN_PATH ?? '/home/wallfacers/.data-talk/opencode/plugins/datatalk-mcp-context.js'

/**
 * Use Python's built-in sqlite3 module instead of the sqlite3 CLI.
 * SQL is written to a temp file to avoid any shell escaping issues with UUIDs.
 */
function sqlite3(sql: string): string {
  const tmpFile = path.join(os.tmpdir(), `dt-sql-${Date.now()}.sql`)
  fs.writeFileSync(tmpFile, sql, 'utf-8')
  try {
    const command = `python3 -c "
import sqlite3, sys
with open(sys.argv[1]) as f:
    sql = f.read()
conn = sqlite3.connect('${DB_PATH.replace(/'/g, "'\\''")}')
conn.execute('PRAGMA busy_timeout=5000')
rows = conn.execute(sql).fetchall()
conn.commit()
for row in rows:
    print('|'.join(str(c) for c in row if c is not None))
conn.close()
" '${tmpFile}'`
    return execSync(command, { encoding: 'utf-8', timeout: 10000 }).trim()
  } finally {
    try { fs.unlinkSync(tmpFile) } catch { /* ignore */ }
  }
}

export function execSqlite(sql: string): void {
  sqlite3(sql)
}

export function getBridgeNonce(): string {
  try {
    const content = fs.readFileSync(PLUGIN_PATH, 'utf-8')
    const match = content.match(/BRIDGE_NONCE\s*=\s*'([^']+)'/)
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
