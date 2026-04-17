import { http } from '@/services/http'

export type Connection = {
  id: string
  kind: 'mysql' | 'postgres' | 'h2'
  host: string
  port: number
  databaseName: string
  username: string
  createdAt: number
}

export async function listConnections(): Promise<Connection[]> {
  const data = await http.get('connections').json<{ connections: Connection[] }>()
  return data.connections
}

export async function createConnection(body: {
  id: string; kind: string; host: string; port: number;
  database: string; username: string; password: string;
}): Promise<void> {
  await http.post('connections', { json: body })
}

export async function updateConnection(id: string, body: {
  kind: string; host: string; port: number;
  database: string; username: string; password: string | null;
}): Promise<void> {
  await http.put(`connections/${id}`, { json: body })
}

export async function deleteConnection(id: string): Promise<void> {
  await http.delete(`connections/${id}`)
}

export async function testConnection(id: string): Promise<{ ok: boolean; latencyMs: number; reason: string | null }> {
  return http.post(`connections/${id}/test`).json<{ ok: boolean; latencyMs: number; reason: string | null }>()
}

export const connectionsKey = ['connections'] as const
