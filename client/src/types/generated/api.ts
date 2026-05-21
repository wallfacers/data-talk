export interface ConnectionDto {
  id: string
  name: string
  kind: string
  host: string
  port: number
  databaseName: string | null
  username: string
  createdAt: number
  connectTimeout: number
  lastTestStatus: string | null
  lastTestAt: number | null
  oracleServiceType: string | null
  sqlserverEncrypt: boolean
  sqlserverTrustServerCertificate: boolean
  sqlserverInstanceName: string | null
  readOnly: boolean
  compatibilityMode: string | null
  oceanbaseTenant: string | null
  oceanbaseCluster: string | null
}

export interface ConnectionCreateRequest {
  name: string
  kind: string
  host: string
  port: number
  databaseName?: string | null  // optional
  username: string
  password: string
  connectTimeout?: number  // optional, defaults to 3000ms
  readOnly?: boolean
  compatibilityMode?: string | null
  oceanbaseTenant?: string | null
  oceanbaseCluster?: string | null
}

export interface ConnectionUpdateRequest {
  name: string
  kind: string
  host: string
  port: number
  databaseName?: string | null  // optional
  username: string
  password: string | null
  connectTimeout?: number  // optional, defaults to 3000ms
  readOnly?: boolean | null
  compatibilityMode?: string | null
  oceanbaseTenant?: string | null
  oceanbaseCluster?: string | null
}

export interface ConnectionTestResultDto {
  ok: boolean
  latencyMs: number
  reason: string | null
}

export interface SessionDto {
  id: string
  connectionId: string | null
  title: string
  hasEverSent: boolean
  createdAt: number
  updatedAt: number
  titleLocked: boolean
  reusedEmpty: boolean
}

export interface SessionCreateRequest {
  connectionId: string | null
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
