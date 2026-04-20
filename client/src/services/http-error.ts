import { toast } from 'sonner'
import { HTTPError } from 'ky'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage, type MessageKey } from '@/i18n/messages'

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

const ERROR_CODE_HINTS: Record<string, MessageKey> = {
  'connection.unreachable': 'error.connection.unreachable',
  'connection.missing': 'error.connection.missing',
  'sql.timeout': 'error.sql.timeout',
  'sql.forbidden': 'error.sql.forbidden',
  'sql.syntax_error': 'error.sql.syntax',
  'upstream.unavailable': 'error.upstream.unavailable',
  'action.timeout': 'error.action.timeout',
  'action.cancelled': 'error.action.cancelled',
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

function t(key: MessageKey, values?: Record<string, string | number>) {
  return translateMessage(getCurrentLanguage(), key, values)
}

export function normalizeError(error: unknown, silent = false): NormalizedError {
  const normalized = new Error() as NormalizedError
  const incomingSilent = (error as Record<string, unknown>)?.silent === true
  normalized.silent = silent || incomingSilent

  if (error instanceof Error && error.name === 'TimeoutError') {
    normalized.type = 'timeout'
    normalized.message = t('error.requestTimeout')
    normalized.code = 'TIMEOUT'
    return normalized
  }

  if (error instanceof HTTPError) {
    normalized.type = 'http'
    normalized.status = error.response.status

    const data = (error as any).data as BackendErrorBody | undefined
    normalized.code = data?.code

    if (data?.code && ERROR_CODE_HINTS[data.code]) {
      normalized.message = t(ERROR_CODE_HINTS[data.code])
    } else if (data?.message) {
      normalized.message = data.message
    } else if (data?.error) {
      normalized.message = data.error
    } else if (error.response.status === 401) {
      normalized.message = t('error.unauthorized')
      normalized.code = 'UNAUTHORIZED'
    } else if (error.response.status === 403) {
      normalized.message = t('error.forbidden')
    } else if (error.response.status === 409) {
      normalized.message = t('error.conflict')
      normalized.code = 'CONFLICT'
    } else if (error.response.status >= 500) {
      normalized.message = t('error.server')
    } else {
      normalized.message = t('error.requestFailed', { status: error.response.status })
    }
    return normalized
  }

  if (error instanceof TypeError && error.message.includes('fetch')) {
    normalized.type = 'network'
    normalized.message = t('error.network')
    normalized.code = 'NETWORK_ERROR'
    return normalized
  }

  normalized.type = 'unknown'
  normalized.message = error instanceof Error ? error.message : t('common.unknownError')
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
