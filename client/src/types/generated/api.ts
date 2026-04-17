export interface ConnectionDto {
  id: string
  kind: string
  host: string
  port: number
  databaseName: string | null  // nullable - null for server-level connection
  username: string
  createdAt: number
}

export interface ConnectionCreateRequest {
  kind: string
  host: string
  port: number
  databaseName?: string | null  // optional
  username: string
  password: string
}

export interface ConnectionUpdateRequest {
  kind: string
  host: string
  port: number
  databaseName?: string | null  // optional
  username: string
  password: string | null
}

export interface ConnectionTestResultDto {
  ok: boolean
  latencyMs: number
  reason: string | null
}

export interface SessionDto {
  id: string
  connectionId: string
  title: string
  hasEverSent: boolean
  createdAt: number
  updatedAt: number
}

export interface SessionCreateRequest {
  connectionId: string
  title: string
}

export interface SessionRenameRequest {
  title: string
}

export interface AiModelsDto {
  providers: AiProviderDto[]
}

export interface AiProviderDto {
  id: string
  name: string
  connected: boolean
  models: AiModelDto[]
}

export interface AiModelDto {
  id: string
  name: string
  enabled: boolean
}

export interface AiCurrentModelDto {
  modelId: string | null
}

export interface AiModelPatchRequest {
  enabled: boolean
}

export interface QueryResponseDto {
  columns: string[]
  rows: Record<string, unknown>[]
  durationMs: number
  rowCount: number
}

export type Connection = ConnectionDto
export type ConnectionCreateInput = ConnectionCreateRequest
export type ConnectionUpdateInput = ConnectionUpdateRequest
export type ConnectionTestResult = ConnectionTestResultDto
export type Session = SessionDto
export type SessionCreateInput = SessionCreateRequest
export type AiProviders = AiModelsDto
export type AiProvider = AiProviderDto
export type AiModel = AiModelDto
export type AiCurrentModel = AiCurrentModelDto
export type QueryResult = QueryResponseDto
