import { describe, expect, it } from 'vitest'
import { classifyImagePayload, computeImagePayloadSize } from './image-payload'
import { IMAGE_PAYLOAD_HARD_LIMIT_BYTES, IMAGE_PAYLOAD_WARN_BYTES } from './constants'

function imagePart(url: string) {
  return {
    type: 'file_upload' as const,
    id: 'p',
    sessionID: 's',
    messageID: '',
    fileId: 'f',
    filename: 'x.png',
    mimeType: 'image/png',
    sizeBytes: 1,
    analysis: {},
    url,
  }
}

describe('computeImagePayloadSize', () => {
  it('returns 0 for an empty parts list', () => {
    expect(computeImagePayloadSize([])).toBe(0)
  })

  it('ignores text parts and image parts without a url', () => {
    const parts = [
      { type: 'text', id: 't', sessionID: 's', messageID: '', text: 'hi', metadata: {} },
      imagePart(''),
      { ...imagePart('data:image/png;base64,AAAA'), url: undefined },
    ]
    expect(computeImagePayloadSize(parts)).toBe(0)
  })

  it('sums url lengths across multiple image parts', () => {
    const parts = [imagePart('a'.repeat(100)), imagePart('b'.repeat(200))]
    expect(computeImagePayloadSize(parts)).toBe(300)
  })

  it('ignores non-image file_upload parts (CSV/JSON)', () => {
    const csv = { ...imagePart('csv-content-but-not-image'), mimeType: 'text/csv' }
    expect(computeImagePayloadSize([csv])).toBe(0)
  })
})

describe('classifyImagePayload', () => {
  it('returns ok below the warn threshold', () => {
    expect(classifyImagePayload(IMAGE_PAYLOAD_WARN_BYTES - 1)).toEqual({
      kind: 'ok',
      bytes: IMAGE_PAYLOAD_WARN_BYTES - 1,
    })
  })

  it('returns ok exactly at the warn threshold', () => {
    expect(classifyImagePayload(IMAGE_PAYLOAD_WARN_BYTES).kind).toBe('ok')
  })

  it('returns warn between warn and hard limit', () => {
    expect(classifyImagePayload(IMAGE_PAYLOAD_WARN_BYTES + 1).kind).toBe('warn')
    expect(classifyImagePayload(IMAGE_PAYLOAD_HARD_LIMIT_BYTES).kind).toBe('warn')
  })

  it('returns reject above the hard limit', () => {
    expect(classifyImagePayload(IMAGE_PAYLOAD_HARD_LIMIT_BYTES + 1).kind).toBe('reject')
  })
})
