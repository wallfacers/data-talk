import type { PatchResult, ExecResult } from './types'

export function patchError(message: string, ...hints: string[]): PatchResult {
  return {
    status: 'error',
    message: hints.length ? `${message}. ${hints.join(' ')}` : message,
  }
}

export function execError(error: string, ...hints: string[]): ExecResult {
  return {
    success: false,
    error: hints.length ? `${error}. ${hints.join(' ')}` : error,
  }
}
