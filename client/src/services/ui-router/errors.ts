import type { PatchResult, ExecResult } from './types'

export type UIErrorDetail = {
  code: string
  message: string
  hint?: string
  availableActions?: string[]
  expectedSchema?: unknown
  currentState?: unknown
}

type PatchErrorResult = PatchResult & { detail: UIErrorDetail }
type ExecErrorResult = ExecResult & { data: UIErrorDetail }

function isUiErrorDetail(value: unknown): value is UIErrorDetail {
  return typeof value === 'object'
    && value !== null
    && typeof (value as UIErrorDetail).code === 'string'
    && typeof (value as UIErrorDetail).message === 'string'
}

function normalizeDetail(messageOrDetail: string | UIErrorDetail, hints: string[]): UIErrorDetail {
  if (typeof messageOrDetail !== 'string') {
    return messageOrDetail
  }

  const hint = hints.join(' ').trim()
  return {
    code: 'error',
    message: messageOrDetail,
    hint: hint || undefined,
  }
}

export function extractUIErrorDetail(value: unknown): UIErrorDetail | undefined {
  if (isUiErrorDetail(value)) {
    return value
  }

  if (typeof value !== 'object' || value === null) {
    return undefined
  }

  const candidate = value as { detail?: unknown; data?: unknown }
  if (isUiErrorDetail(candidate.detail)) {
    return candidate.detail
  }
  if (isUiErrorDetail(candidate.data)) {
    return candidate.data
  }
  return undefined
}

export function patchError(message: string, ...hints: string[]): PatchErrorResult
export function patchError(detail: UIErrorDetail): PatchErrorResult
export function patchError(messageOrDetail: string | UIErrorDetail, ...hints: string[]): PatchErrorResult {
  const detail = normalizeDetail(messageOrDetail, hints)
  return {
    status: 'error',
    message: detail.message,
    detail,
  }
}

export function execError(error: string, ...hints: string[]): ExecErrorResult
export function execError(detail: UIErrorDetail): ExecErrorResult
export function execError(errorOrDetail: string | UIErrorDetail, ...hints: string[]): ExecErrorResult {
  const detail = normalizeDetail(errorOrDetail, hints)
  return {
    success: false,
    error: detail.message,
    data: detail,
  }
}
