import type { PatchResult, ExecResult } from './types'

/**
 * Concrete follow-up action an LLM can invoke to recover from a UI router error.
 * Mirrors the shape of `datatalk_ui_exec` input so a caller can pass it through verbatim.
 */
export type UIErrorNextAction = {
  object: string
  action: string
  params?: Record<string, unknown>
}

export type UIErrorDetail = {
  code: string
  message: string
  hint?: string
  availableActions?: string[]
  expectedSchema?: unknown
  currentState?: unknown
  /**
   * Optional self-healing hint: the single follow-up action that would recover from this error.
   * Present for actionable failures like `no_active_query_editor`, omitted for ambiguous failures
   * like `version_conflict` (where recovery requires AI to re-read state, not one fixed action).
   */
  nextAction?: UIErrorNextAction
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

/**
 * Builds the structured "no active target" error returned by {@link UIRouter.handle} when
 * a request targets an object type that has no resolvable instance (typically because the
 * corresponding tab is not open). For tab-based objects (`query_editor`, `er_inspector`,
 * `er_designer`, `script_editor`) the error includes a {@link UIErrorNextAction} guiding the
 * caller to open the right tab. For singletons / unknown types the nextAction is omitted.
 *
 * The shape matches the contract documented in
 * `openspec/changes/dialect-aware-import-export-and-friction-fix/specs/ui-exec-error-semantics/spec.md`.
 */
export function noActiveTargetError(objectType: string): UIErrorDetail {
  const code = `no_active_${objectType}`
  const message = `No active ${objectType} tab`
  switch (objectType) {
    case 'query_editor':
      return {
        code,
        message,
        hint: 'Open a query_editor tab first via workspace.open before targeting it.',
        nextAction: {
          object: 'workspace',
          action: 'open',
          params: { type: 'query_editor', title: 'Untitled SQL' },
        },
      }
    case 'er_inspector':
      return {
        code,
        message,
        hint: 'Open an er_inspector tab first via workspace.open_er_inspector.',
        nextAction: {
          object: 'workspace',
          action: 'open_er_inspector',
          params: { tables: [], title: 'ER Inspector' },
        },
      }
    case 'er_designer':
      return {
        code,
        message,
        hint: 'Open an er_designer tab first via workspace.open_er_designer.',
        nextAction: {
          object: 'workspace',
          action: 'open_er_designer',
          params: { dialect: 'mysql', title: 'ER Designer' },
        },
      }
    case 'script_editor':
      return {
        code,
        message,
        hint: 'Open a script_editor tab first via workspace.open before targeting it.',
        nextAction: {
          object: 'workspace',
          action: 'open',
          params: { type: 'script_editor', title: 'Untitled Script' },
        },
      }
    case 'dashboard':
      return {
        code,
        message,
        hint: 'Open a dashboard tab first via workspace.open before targeting it.',
        nextAction: {
          object: 'workspace',
          action: 'open',
          params: { type: 'dashboard', title: 'Untitled Dashboard' },
        },
      }
    default:
      return {
        code,
        message,
        hint: `No instance of '${objectType}' is currently registered. Open or create one before targeting it.`,
      }
  }
}

/**
 * Builds the structured "unknown target" error returned when an explicit target id is given
 * but does not resolve (wrong type, deleted tab, or never existed). Distinct from
 * {@link noActiveTargetError} which covers the target='active' case.
 */
export function unknownTargetError(objectType: string, target: string): UIErrorDetail {
  return {
    code: 'unknown_target',
    message: `No ${objectType} found for target '${target}'`,
    hint: 'Verify the target id and object type. The tab may have been closed or never registered.',
  }
}
