import { http } from '@/services/http'
import { HTTPError } from 'ky'
import type { Connection, ConnectionCreateInput } from '@/types/generated/api'
import type { ConnectionTargetsResponse } from '@/services/api/session-data-context'

export type { Connection } from '@/types/generated/api'
export type { ConnectionTargetsResponse } from '@/services/api/session-data-context'
export type DbType = 'mysql' | 'postgres' | 'h2' | 'sqlite' | 'mariadb' | 'oracle' | 'sqlserver' | 'duckdb' | 'clickhouse' | 'apache_doris'

// Alias for create input with password
export type CreateConnectionInput = ConnectionCreateInput

export function listConnections() {
  return http.get('connections').json<{ connections: Connection[] }>().then((r) => r.connections)
}

export function createConnection(input: CreateConnectionInput) {
  return http.post('connections', { json: input }).json<Connection>()
}

export function testConnection(id: string) {
  return http.post(`connections/${id}/test`).json<{ ok: boolean; message?: string }>()
}

export function getConnectionTargets(connectionId: string) {
  return http.get(`connections/${connectionId}/targets`).json<ConnectionTargetsResponse>()
}

export interface ConnectionDeleteBlocked {
  connectionId: string
  counts: {
    sessions: number
    candidates: number
    temporary: number
    archived: number
  }
}

/**
 * Delete a connection. If the server returns 409 (blocked by resources),
 * returns the blocked info; otherwise returns void.
 */
export async function deleteConnection(id: string, options?: { force?: boolean }): Promise<ConnectionDeleteBlocked | void> {
  const params = options?.force ? { force: 'true' } : undefined
  try {
    await http.delete(`connections/${id}`, { searchParams: params })
  } catch (error: unknown) {
    if (error instanceof HTTPError && error.response.status === 409) {
      return error.response.json() as Promise<ConnectionDeleteBlocked>
    }
    throw error
  }
}
