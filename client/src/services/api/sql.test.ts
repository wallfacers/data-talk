import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { executeSql } from './sql'

describe('executeSql', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => vi.unstubAllGlobals())

  it('parses an executed response', async () => {
    (fetch as any).mockResolvedValue(
      new Response(JSON.stringify({ status: 'executed', resolvedContext: {}, results: [] }),
        { status: 200, headers: { 'content-type': 'application/json' } })
    )
    const out = await executeSql({ connectionId: 'c1', sql: 'SELECT 1', source: 'user' })
    expect(out.status).toBe('executed')
  })

  it('parses a requires_confirmation response', async () => {
    (fetch as any).mockResolvedValue(
      new Response(JSON.stringify({
        status: 'requires_confirmation', resolvedContext: {},
        confirmation: { level: 'L2', reason: 'update_with_where', affectedObjects: ['t'], sqlPreview: 'UPDATE t SET x=1 WHERE id=1' },
      }), { status: 200, headers: { 'content-type': 'application/json' } })
    )
    const out = await executeSql({ connectionId: 'c1', sql: 'UPDATE t SET x=1 WHERE id=1', source: 'user' })
    expect(out.status).toBe('requires_confirmation')
    if (out.status === 'requires_confirmation') {
      expect(out.confirmation.level).toBe('L2')
    }
  })

  it('parses a confirmation_invalid response', async () => {
    (fetch as any).mockResolvedValue(
      new Response(JSON.stringify({
        status: 'confirmation_invalid', resolvedContext: {},
        invalidConfirmation: { reason: 'risk_ack_insufficient', ackedRisk: 'L1', currentRisk: 'L2', message: 'm' },
      }), { status: 200, headers: { 'content-type': 'application/json' } })
    )
    const out = await executeSql({ connectionId: 'c1', sql: 'UPDATE t SET x=1 WHERE id=1', source: 'user', confirmed: true, riskAck: 'L1' })
    expect(out.status).toBe('confirmation_invalid')
  })

  it('sends confirmed and riskAck fields when provided', async () => {
    const bodySpy = vi.fn()
    ;(fetch as any).mockImplementation(async (_url: string, opts: any) => {
      bodySpy(JSON.parse(opts.body))
      return new Response(JSON.stringify({ status: 'executed', resolvedContext: {}, results: [] }),
        { status: 200, headers: { 'content-type': 'application/json' } })
    })
    await executeSql({ connectionId: 'c1', sql: 'DROP TABLE t', source: 'user', confirmed: true, riskAck: 'L3' })
    expect(bodySpy).toHaveBeenCalledWith(expect.objectContaining({ confirmed: true, riskAck: 'L3' }))
  })

  it('omits confirmed and riskAck when not provided', async () => {
    const bodySpy = vi.fn()
    ;(fetch as any).mockImplementation(async (_url: string, opts: any) => {
      bodySpy(JSON.parse(opts.body))
      return new Response(JSON.stringify({ status: 'executed', resolvedContext: {}, results: [] }),
        { status: 200, headers: { 'content-type': 'application/json' } })
    })
    await executeSql({ connectionId: 'c1', sql: 'SELECT 1', source: 'user' })
    const body = bodySpy.mock.calls[0][0]
    expect(body).not.toHaveProperty('confirmed')
    expect(body).not.toHaveProperty('riskAck')
  })
})
