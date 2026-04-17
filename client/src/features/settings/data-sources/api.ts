import { http } from '@/services/http'
import type { Connection, ConnectionTestResult, ConnectionCreateInput, ConnectionUpdateInput } from '@/types/generated/api'

export type { Connection, ConnectionTestResult } from '@/types/generated/api'

export async function listConnections(): Promise<Connection[]> {
  const data = await http.get('connections').json<{ connections: Connection[] }>()
  return data.connections
}

export async function createConnection(body: ConnectionCreateInput): Promise<void> {
  await http.post('connections', { json: body })
}

export async function updateConnection(id: string, body: ConnectionUpdateInput): Promise<void> {
  await http.put(`connections/${id}`, { json: body })
}

export async function deleteConnection(id: string): Promise<void> {
  await http.delete(`connections/${id}`)
}

export async function testConnection(id: string): Promise<ConnectionTestResult> {
  return http.post(`connections/${id}/test`).json<ConnectionTestResult>()
}

export const connectionsKey = ['connections'] as const