import { create } from 'zustand'

export type DataSourcePickResult =
  | { connectionId: string; connectionName: string }
  | { cancelled: true }

type RequestOptions = {
  reason?: string | null
  preferredConnectionId?: string | null
}

type PendingResolver = (result: DataSourcePickResult) => void

type DataSourcePickerState = {
  open: boolean
  reason: string | null
  preferredConnectionId: string | null
  resolver: PendingResolver | null
  requestPick: (options?: RequestOptions) => Promise<DataSourcePickResult>
  resolvePick: (result: Extract<DataSourcePickResult, { connectionId: string }>) => void
  cancelPick: () => void
  reset: () => void
}

function clearState() {
  return {
    open: false,
    reason: null,
    preferredConnectionId: null,
    resolver: null,
  }
}

export const useDataSourcePickerStore = create<DataSourcePickerState>((set, get) => ({
  ...clearState(),

  requestPick: (options) => {
    const existing = get().resolver
    if (existing) existing({ cancelled: true })

    return new Promise<DataSourcePickResult>((resolve) => {
      set({
        open: true,
        reason: options?.reason ?? null,
        preferredConnectionId: options?.preferredConnectionId ?? null,
        resolver: resolve,
      })
    })
  },

  resolvePick: (result) => {
    const resolver = get().resolver
    resolver?.(result)
    set(clearState())
  },

  cancelPick: () => {
    const resolver = get().resolver
    resolver?.({ cancelled: true })
    set(clearState())
  },

  reset: () => {
    const resolver = get().resolver
    resolver?.({ cancelled: true })
    set(clearState())
  },
}))
