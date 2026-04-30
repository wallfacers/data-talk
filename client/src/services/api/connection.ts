import { http } from '@/services/http'
import type { Connection, ConnectionCreateInput } from '@/types/generated/api'
import type { ConnectionTargetsResponse } from '@/services/api/session-data-context'

export type { Connection } from '@/types/generated/api'
export type { ConnectionTargetsResponse } from '@/services/api/session-data-context'
export type DbType = 'mysql' | 'postgres' | 'h2' | 'sqlite'

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
