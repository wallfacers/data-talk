import { describe, it, expect, vi, beforeEach } from 'vitest'

const getMock = vi.fn()
vi.mock('@/services/http', () => ({
  http: {
    get: (...args: unknown[]) => getMock(...args),
  },
}))

import { fetchSessionStatus } from './session-status'

function jsonResponse<T>(value: T) {
  return { json: async () => value as T }
}

function rejectingResponse(err: unknown) {
  return { json: async () => { throw err } }
}

describe('fetchSessionStatus (BUG-0046)', () => {
  beforeEach(() => {
    getMock.mockReset()
  })

  it('returns busy when backend reports busy', async () => {
    getMock.mockReturnValueOnce(jsonResponse({ type: 'busy' }))

    const status = await fetchSessionStatus('ses_a')

    expect(status).toEqual({ type: 'busy' })
    expect(getMock).toHaveBeenCalledWith('sessions/ses_a/status', expect.objectContaining({ silent: true }))
  })

  it('returns retry when backend reports retry', async () => {
    getMock.mockReturnValueOnce(jsonResponse({ type: 'retry' }))

    const status = await fetchSessionStatus('ses_b')

    expect(status).toEqual({ type: 'retry' })
  })

  it('returns idle when backend reports idle', async () => {
    getMock.mockReturnValueOnce(jsonResponse({ type: 'idle' }))

    const status = await fetchSessionStatus('ses_c')

    expect(status).toEqual({ type: 'idle' })
  })

  it('fail-opens to idle on network error (request throws)', async () => {
    getMock.mockImplementationOnce(() => { throw new Error('connection refused') })

    const status = await fetchSessionStatus('ses_d')

    expect(status).toEqual({ type: 'idle' })
  })

  it('fail-opens to idle on json() throwing (HTTP error)', async () => {
    getMock.mockReturnValueOnce(rejectingResponse(new Error('HTTP 500')))

    const status = await fetchSessionStatus('ses_e')

    expect(status).toEqual({ type: 'idle' })
  })

  it('fail-opens to idle on unknown type', async () => {
    getMock.mockReturnValueOnce(jsonResponse({ type: 'weird-mode' }))

    const status = await fetchSessionStatus('ses_f')

    expect(status).toEqual({ type: 'idle' })
  })

  it('returns idle when sessionId is empty without making a request', async () => {
    const status = await fetchSessionStatus('')

    expect(status).toEqual({ type: 'idle' })
    expect(getMock).not.toHaveBeenCalled()
  })
})
