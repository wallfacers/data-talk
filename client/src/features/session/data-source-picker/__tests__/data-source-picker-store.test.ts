import { afterEach, describe, expect, it } from 'vitest'
import { useDataSourcePickerStore } from '../data-source-picker-store'

describe('data-source-picker-store', () => {
  afterEach(() => {
    useDataSourcePickerStore.getState().reset()
  })

  it('requestPick opens chooser and resolvePick resolves selected connection', async () => {
    const promise = useDataSourcePickerStore.getState().requestPick({
      reason: 'send',
      preferredConnectionId: 'c2',
    })

    expect(useDataSourcePickerStore.getState().open).toBe(true)
    expect(useDataSourcePickerStore.getState().reason).toBe('send')
    expect(useDataSourcePickerStore.getState().preferredConnectionId).toBe('c2')

    useDataSourcePickerStore.getState().resolvePick({
      connectionId: 'c2',
      connectionName: 'orders-prod',
    })

    await expect(promise).resolves.toEqual({
      connectionId: 'c2',
      connectionName: 'orders-prod',
    })
    expect(useDataSourcePickerStore.getState().open).toBe(false)
    expect(useDataSourcePickerStore.getState().reason).toBeNull()
    expect(useDataSourcePickerStore.getState().preferredConnectionId).toBeNull()
  })

  it('cancelPick resolves cancelled result and clears chooser state', async () => {
    const promise = useDataSourcePickerStore.getState().requestPick({
      reason: 'ui_exec',
    })

    useDataSourcePickerStore.getState().cancelPick()

    await expect(promise).resolves.toEqual({ cancelled: true })
    expect(useDataSourcePickerStore.getState().open).toBe(false)
    expect(useDataSourcePickerStore.getState().reason).toBeNull()
  })
})
