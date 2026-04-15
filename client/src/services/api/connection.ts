import { http } from '@/services/http'

export type DbType = 'mysql' | 'postgres' | 'sqlserver' | 'oracle' | 'sqlite'

export type Connection = {
  id: string
  name: string
  dbType: DbType
  host: string
  port: number
  database: string
  username: string
}

export type CreateConnectionInput = Omit<Connection, 'id'> & { password: string }

export function listConnections() {
  return http.get('connections').json<Connection[]>()
}

export function createConnection(input: CreateConnectionInput) {
  return http.post('connections', { json: input }).json<Connection>()
}

export function testConnection(id: string) {
  return http.post(`connections/${id}/test`).json<{ ok: boolean; message?: string }>()
}
