import type { UIObject, UIRequest, UIResponse, UIObjectInfo, ActionDef, PatchResult } from './types'
import { patchError, execError } from './errors'
import { matchPathPattern } from './pathResolver'

export class UIRouter {
  private instances = new Map<string, UIObject>()
  private _getActiveTabId: (() => string | null) | null = null

  setActiveTabIdProvider(fn: () => string | null) { this._getActiveTabId = fn }

  registerInstance(objectId: string, instance: UIObject) { this.instances.set(objectId, instance) }
  unregisterInstance(objectId: string) { this.instances.delete(objectId) }

  async handle(req: UIRequest): Promise<UIResponse> {
    if (req.tool === 'ui_list') {
      return this.handleList((req.payload as { filter?: ListFilter } | undefined)?.filter)
    }
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
    return {
      data: result,
      status: result.status === 'error' ? undefined : result.status,
      confirm_id: result.confirm_id,
      error: result.status === 'error' ? result.message : undefined,
    }
  }

  private async handlePatch(instance: UIObject, payload: unknown): Promise<UIResponse> {
    const p = (payload ?? {}) as { ops?: Array<{ op: 'add' | 'remove' | 'replace'; path: string; value?: unknown }>; reason?: string }
    const ops = p.ops ?? []
    const caps = instance.patchCapabilities
    if (!caps?.length) {
      const result = await instance.patch(ops, p.reason)
      return this.patchResponse(result)
    }
    for (const op of ops) {
      const match = caps.find((cap) => cap.ops.includes(op.op) && matchPathPattern(op.path, cap.pathPattern))
      if (!match) {
        const supported = caps.map((c) => `${c.ops.join('/')} ${c.pathPattern}`).join(', ')
        const err = patchError(`Unsupported: ${op.op} ${op.path}`, `Supported paths: [${supported}]`)
        return { error: err.message }
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
        const available = actions.map((a) => a.name).join(', ')
        const err = execError(`Unknown action '${action}'`, `Available: [${available}]`)
        return { data: err, error: err.error }
      }
      const required = def.paramsSchema?.required ?? []
      const missing = required.filter((k) => (params as Record<string, unknown> | undefined)?.[k] === undefined)
      if (missing.length) {
        const err = execError(`Missing required params: ${missing.join(', ')}`, `Schema: ${JSON.stringify(def.paramsSchema)}`)
        return { data: err, error: err.error }
      }
    }

    const result = await instance.exec(action, params)
    return { data: result, error: result.success ? undefined : result.error }
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
      if (direct) return !objectType || direct.type === objectType ? direct : null
      for (const [, obj] of this.instances) {
        if (obj.tabId === activeTabId) return !objectType || obj.type === objectType ? obj : null
      }
      return null
    }
    for (const [, obj] of this.instances) {
      if (!objectType || obj.type === objectType) return obj
    }
    return null
  }

  private handleList(filter?: ListFilter): UIResponse {
    const results: UIObjectInfo[] = []
    for (const [, obj] of this.instances) {
      if (filter?.type && obj.type !== filter.type) continue
      if (filter?.connectionId != null && obj.connectionId !== filter.connectionId) continue
      if (filter?.keyword) {
        const hay = `${obj.title} ${obj.objectId}`.toLowerCase()
        if (!hay.includes(filter.keyword.toLowerCase())) continue
      }
      results.push({ objectId: obj.objectId, type: obj.type, title: obj.title, connectionId: obj.connectionId, database: obj.database })
    }
    return { data: results }
  }
}

type ListFilter = { type?: string; keyword?: string; connectionId?: string; database?: string }

export const uiRouter = new UIRouter()
