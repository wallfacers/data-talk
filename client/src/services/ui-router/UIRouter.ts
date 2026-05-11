import type { UIObject, UIRequest, UIResponse, ActionDef, PatchResult } from './types'
import { patchError, execError, extractUIErrorDetail, type UIErrorDetail } from './errors'
import { matchPathPattern } from './pathResolver'

export class UIRouter {
  private instances = new Map<string, UIObject>()
  private _getActiveTabId: (() => string | null) | null = null

  setActiveTabIdProvider(fn: () => string | null) { this._getActiveTabId = fn }

  registerInstance(objectId: string, instance: UIObject) { this.instances.set(objectId, instance) }
  unregisterInstance(objectId: string) { this.instances.delete(objectId) }

  async handle(req: UIRequest): Promise<UIResponse> {
    const instance = this.resolveTarget(req.object, req.target)
    if (!instance) return { error: `No ${req.object} found for target '${req.target}'` }

    try {
      switch (req.tool) {
        case 'ui_read': {
          const mode = (req.payload as { mode?: 'state' | 'schema' | 'actions' | 'full' } | undefined)?.mode ?? 'state'
          return { data: instance.read(mode) }
        }
        case 'ui_patch':
          return this.handlePatch(instance, req.payload)
        case 'ui_exec':
          return this.handleExec(instance, req.payload)
      }
    } catch (e) {
      return { error: String(e) }
    }
    return { error: `Unknown tool: ${req.tool}` }
  }

  private patchResponse(result: PatchResult): UIResponse {
    const detail = extractUIErrorDetail(result)
    return {
      data: result.status === 'error' ? detail ?? result : result,
      status: result.status === 'error' ? undefined : result.status,
      confirm_id: result.confirm_id,
      error: result.status === 'error' ? detail?.message ?? result.message : undefined,
    }
  }

  private async handlePatch(instance: UIObject, payload: unknown): Promise<UIResponse> {
    const p = (payload ?? {}) as {
      ops?: Array<{ op: 'add' | 'remove' | 'replace'; path: string; value?: unknown; baseVersion?: number | 'auto'; expectedVersion?: number | 'auto' }>
      reason?: string
      baseVersion?: number | 'auto'
    }
    const ops = (p.ops ?? []).map((op) => (
      p.baseVersion !== undefined && op.baseVersion === undefined && op.expectedVersion === undefined
        ? { ...op, baseVersion: p.baseVersion }
        : op
    ))
    const caps = instance.patchCapabilities
    if (!caps?.length) {
      const result = await instance.patch(ops, p.reason)
      return this.patchResponse(result)
    }
    for (const op of ops) {
      const match = caps.find((cap) => cap.ops.includes(op.op) && matchPathPattern(op.path, cap.pathPattern))
      if (!match) {
        const supportedPaths = caps
          .filter((cap) => cap.ops.includes(op.op))
          .map((cap) => cap.pathPattern)
        const availableActions = this.getAvailableActionNames(instance)
        const detail = this.buildPatchCapabilityError(op.op, op.path, supportedPaths, availableActions)
        const err = patchError(detail)
        return { data: err.detail, error: err.message }
      }
    }
    const result = await instance.patch(ops, p.reason)
    return this.patchResponse(result)
  }

  private async handleExec(instance: UIObject, payload: unknown): Promise<UIResponse> {
    const p = (payload ?? {}) as { action?: string; params?: unknown }
    const action = p.action ?? ''
    const params = p.params

    const rawActions = instance.read('actions')
    if (Array.isArray(rawActions) && rawActions.length > 0) {
      const actions = rawActions as ActionDef[]
      const def = actions.find((a) => a.name === action)
      if (!def) {
        const detail: UIErrorDetail = {
          code: 'unknown_action',
          message: `Unknown action '${action}'`,
          hint: "Use `ui_read(mode='actions')` to inspect supported actions, or switch to `ui_patch('/content', 'replace')` for a full SQL rewrite.",
          availableActions: actions.map((item) => item.name),
        }
        const err = execError(detail)
        return { data: err.data, error: err.error }
      }
      const required = def.paramsSchema?.required ?? []
      const anyOfGroups = def.paramsSchema?.anyOf ?? []
      const anyOfFields = new Set(anyOfGroups.flatMap(g => g.required ?? []))

      const missing = required.filter((k) => {
        if (anyOfFields.has(k)) return false
        return (params as Record<string, unknown> | undefined)?.[k] === undefined
      })
      const missingAnyOf = this.missingAnyOfRequiredGroup(def, params)
      if (missing.length > 0 || missingAnyOf !== null) {
        const parts = [...missing, ...(missingAnyOf ? [missingAnyOf] : [])]
        const detail: UIErrorDetail = {
          code: 'invalid_params',
          message: `Missing required params for action '${action}': ${parts.join(', ')}`,
          hint: `Provide the required fields and match the action schema for '${action}'.`,
          expectedSchema: def.paramsSchema,
        }
        const err = execError(detail)
        return { data: err.data, error: err.error }
      }
    }

    const result = await instance.exec(action, params)
    if (result.success) {
      return { data: result, error: undefined }
    }

    const detail = extractUIErrorDetail(result)
    return {
      data: detail ?? result,
      error: detail?.message ?? result.error,
    }
  }

  private missingAnyOfRequiredGroup(def: ActionDef, params: unknown): string | null {
    const anyOf = def.paramsSchema?.anyOf ?? []
    if (!anyOf.length) return null

    const input = params as Record<string, unknown> | undefined
    const groups = anyOf
      .map((branch) => branch.required ?? [])
      .filter((required) => required.length > 0)
    if (!groups.length) return null

    const satisfied = groups.some((required) => required.every((key) => input?.[key] !== undefined))
    if (satisfied) return null

    const fields = Array.from(new Set(groups.flat()))
    return `one of ${fields.join(', ')}`
  }

  private resolveTarget(objectType: string, target: string): UIObject | null {
    if (target && target !== 'active') {
      const direct = this.instances.get(target) ?? null
      if (!direct) return null
      return !objectType || direct.type === objectType ? direct : null
    }
    const activeTabId = this._getActiveTabId?.()
    if (target === 'active' && activeTabId) {
      const direct = this.instances.get(activeTabId)
      if (direct && (!objectType || direct.type === objectType)) return direct
      for (const [, obj] of this.instances) {
        if (obj.tabId === activeTabId && (!objectType || obj.type === objectType)) return obj
      }
      // Singletons (e.g. workspace) are registered at objectId === type and are not
      // tied to any stage tab. target='active' has no meaning for them, but LLM
      // handlers default to it — so fall back to the type-named instance here.
      // Tab-based types (query_editor, report, ...) stay strict: no substitution.
      if (objectType) {
        const singleton = this.instances.get(objectType)
        if (singleton && singleton.type === objectType) return singleton
      }
      return null
    }
    for (const [, obj] of this.instances) {
      if (!objectType || obj.type === objectType) return obj
    }
    return null
  }

  private getAvailableActionNames(instance: UIObject): string[] | undefined {
    const rawActions = instance.read('actions')
    if (!Array.isArray(rawActions) || rawActions.length === 0) {
      return undefined
    }
    return (rawActions as ActionDef[]).map((action) => action.name)
  }

  private buildPatchCapabilityError(
    op: 'add' | 'remove' | 'replace',
    path: string,
    supportedPaths: string[],
    availableActions?: string[],
  ): UIErrorDetail {
    const supportedPathsText = supportedPaths.length > 0 ? supportedPaths.join(', ') : 'none'
    return {
      code: 'unsupported_patch',
      message: `Unsupported patch: ${op} ${path}`,
      hint: `Use replace only on supported paths: ${supportedPathsText}. Re-read patch capabilities if you need to confirm the allowed paths.`,
      availableActions,
    }
  }
}

export const uiRouter = new UIRouter()
