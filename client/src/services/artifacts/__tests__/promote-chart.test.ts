import { beforeEach, describe, expect, it, vi } from 'vitest'
import { promoteChartToStage } from '../promote-chart'

describe('promoteChartToStage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('posts the chart option and parses the created artifact response', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ artifactId: 'art-42', version: 3 }),
    })
    global.fetch = fetchMock as any

    await expect(promoteChartToStage({
      sessionId: 'sess-1',
      option: { series: [{ type: 'bar' }] },
      sourceArtifactId: 'art-src',
      originMessageId: 'msg-7',
      originPartId: 'part-2',
    })).resolves.toEqual({ artifactId: 'art-42', version: 3 })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/sessions/sess-1/artifacts/chart')
    expect(init.method).toBe('POST')
    expect(init.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(JSON.parse(init.body)).toEqual({
      echartsOption: { series: [{ type: 'bar' }] },
      sourceArtifactId: 'art-src',
      originMessageId: 'msg-7',
      originPartId: 'part-2',
    })
  })

  it('throws status and body text on non-2xx responses', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      text: async () => 'upstream chart service unavailable',
    })
    global.fetch = fetchMock as any

    await expect(promoteChartToStage({
      sessionId: 'sess-1',
      option: { series: [] },
    })).rejects.toThrow('POST /api/sessions/sess-1/artifacts/chart failed with 502: upstream chart service unavailable')
  })
})
