import { createRoot } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { acquireNodeRoot, scheduleRootUnmount } from '../markdown'

const flushMicrotasks = async () => {
  await Promise.resolve()
  await new Promise((resolve) => setTimeout(resolve, 0))
}

const ALREADY_ROOTED = /already been passed to createRoot/

afterEach(() => {
  vi.restoreAllMocks()
})

describe('markdown nested root lifecycle', () => {
  it('logs the createRoot warning when a node is rooted twice directly (the reported bug)', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const node = document.createElement('div')
    document.body.appendChild(node)
    createRoot(node)
    createRoot(node)
    expect(spy.mock.calls.some((args) => args.some((a) => ALREADY_ROOTED.test(String(a))))).toBe(true)
    document.body.removeChild(node)
  })

  it('reuses the root and cancels a pending unmount on StrictMode remount (no warning)', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const node = document.createElement('div')
    document.body.appendChild(node)

    const r1 = acquireNodeRoot(node)
    // StrictMode cleanup: deferred unmount
    scheduleRootUnmount(node)
    // StrictMode re-invokes the mount effect before the microtask flushes
    const r2 = acquireNodeRoot(node)
    expect(r2).toBe(r1)

    await flushMicrotasks()

    // The cancelled unmount must not have torn the root down
    expect(acquireNodeRoot(node)).toBe(r1)
    // And no double-createRoot warning was emitted
    expect(spy.mock.calls.some((args) => args.some((a) => ALREADY_ROOTED.test(String(a))))).toBe(false)

    document.body.removeChild(node)
  })

  it('unmounts for real when the node is not re-adopted', async () => {
    const node = document.createElement('div')
    document.body.appendChild(node)

    const r1 = acquireNodeRoot(node)
    scheduleRootUnmount(node)
    await flushMicrotasks()

    const r2 = acquireNodeRoot(node)
    expect(r2).not.toBe(r1)

    document.body.removeChild(node)
  })
})
