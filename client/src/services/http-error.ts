import { toast } from 'sonner'
import { HTTPError } from 'ky'

export interface NormalizedError extends Error {
  message: string
  code?: string
  status?: number
  type: 'http' | 'network' | 'timeout' | 'unknown'
  silent?: boolean
}

interface BackendErrorBody {
  error?: string
  message?: string
  code?: string
}

const ERROR_CODE_HINTS: Record<string, string> = {
  'connection.unreachable': '数据库连接失败，请检查网络和连接配置',
  'connection.missing': '请先选择数据源',
  'sql.timeout': '查询超时，请优化 SQL 或缩小查询范围',
  'sql.forbidden': '安全限制：仅支持 SELECT 查询',
  'sql.syntax_error': 'SQL 语法错误',
  'upstream.unavailable': 'AI 服务暂不可用，请稍后重试',
  'action.timeout': '操作超时',
  'action.cancelled': '操作已取消',
}

const DEDUPE_WINDOW_MS = 3000
const recentErrors = new Map<string, number>()

function cleanupExpired() {
  const now = Date.now()
  for (const [key, timestamp] of recentErrors) {
    if (now - timestamp > DEDUPE_WINDOW_MS) {
      recentErrors.delete(key)
    }
  }
}

export function getDedupeKey(error: NormalizedError): string {
  if (error.type === 'network') return 'network_error'
  if (error.type === 'timeout') return 'timeout_error'
  if (error.code) return `code:${error.code}`
  return `msg:${error.message.slice(0, 50)}`
}

export type MessageRole = 'user' | 'assistant' | 'system'

export function normalizeRole(role: unknown): MessageRole {
  return String(role ?? 'assistant').toLowerCase() as MessageRole
}

export function normalizeError(error: unknown, silent = false): NormalizedError {
  const normalized = new Error() as NormalizedError
  const incomingSilent = (error as Record<string, unknown>)?.silent === true
  normalized.silent = silent || incomingSilent

  if (error instanceof Error && error.name === 'TimeoutError') {
    normalized.type = 'timeout'
    normalized.message = '请求超时，请稍后重试'
    normalized.code = 'TIMEOUT'
    return normalized
  }

  if (error instanceof HTTPError) {
    normalized.type = 'http'
    normalized.status = error.response.status

    const data = (error as any).data as BackendErrorBody | undefined
    normalized.code = data?.code

    if (data?.code && ERROR_CODE_HINTS[data.code]) {
      normalized.message = ERROR_CODE_HINTS[data.code]
    } else if (data?.message) {
      normalized.message = data.message
    } else if (data?.error) {
      normalized.message = data.error
    } else if (error.response.status === 401) {
      normalized.message = '请重新登录'
      normalized.code = 'UNAUTHORIZED'
    } else if (error.response.status === 403) {
      normalized.message = '无权限执行此操作'
    } else if (error.response.status >= 500) {
      normalized.message = '服务器错误，请稍后重试'
    } else {
      normalized.message = `请求失败 (${error.response.status})`
    }
    return normalized
  }

  if (error instanceof TypeError && error.message.includes('fetch')) {
    normalized.type = 'network'
    normalized.message = '网络连接失败，请检查网络'
    normalized.code = 'NETWORK_ERROR'
    return normalized
  }

  normalized.type = 'unknown'
  normalized.message = error instanceof Error ? error.message : '未知错误'
  return normalized
}

export function showErrorToast(error: NormalizedError): void {
  if (error.silent) return

  cleanupExpired()

  const key = getDedupeKey(error)
  const now = Date.now()
  const lastShown = recentErrors.get(key)

  if (lastShown && now - lastShown < DEDUPE_WINDOW_MS) {
    return
  }

  recentErrors.set(key, now)
  toast.error(error.message, { id: key })
}