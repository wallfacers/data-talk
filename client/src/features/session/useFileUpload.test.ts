import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'

// Mock the uploadFile API. Default export resolves successfully — individual tests
// override the mock implementation as needed (controlled promises, abort behaviour).
vi.mock('@/services/api/file-upload', () => ({
  uploadFile: vi.fn().mockResolvedValue({
    fileId: 'test-id',
    filename: 'test.png',
    mimeType: 'image/png',
    sizeBytes: 100,
    analysis: { type: 'IMAGE', summary: { width: 10, height: 10 } },
  }),
}))

// Mock i18n — return the key as value (suffixed with params) for assertion
vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, string>) => {
      if (!params) return key
      return key + ':' + Object.entries(params).map(([k, v]) => `${k}=${v}`).join(',')
    },
  }),
}))

import { useFileUpload } from './useFileUpload'
import { uploadFile } from '@/services/api/file-upload'
import type { FileUploadResponse } from '@/services/api/file-upload'

const mockUploadFile = vi.mocked(uploadFile)

function makeFile(name: string, size = 1024, type = 'application/octet-stream'): File {
  const file = new File(['x'.repeat(size)], name, { type })
  return file
}

function makeResponse(fileId: string): FileUploadResponse {
  return {
    fileId,
    filename: `${fileId}.png`,
    mimeType: 'image/png',
    sizeBytes: 100,
    analysis: { type: 'IMAGE', fullContent: false, summary: { width: 10, height: 10 } },
  }
}

// Flush queued microtasks. Two passes catch the case where one microtask schedules another
// (e.g. addFiles → queueMicrotask(scheduleUpload) → setAttachments → effect refresh).
async function flushMicrotasks(): Promise<void> {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}

describe('useFileUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Default: instant resolve. Override per-test for controlled timing.
    mockUploadFile.mockResolvedValue({
      fileId: 'test-id',
      filename: 'test.png',
      mimeType: 'image/png',
      sizeBytes: 100,
      analysis: { type: 'IMAGE', fullContent: false, summary: { width: 10, height: 10 } },
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('accepts image extensions (.png, .jpg, .jpeg, .gif, .webp, .bmp)', () => {
    const { result } = renderHook(() => useFileUpload('session-1'))
    const imageNames = ['a.png', 'b.jpg', 'c.jpeg', 'd.gif', 'e.webp', 'f.bmp']

    act(() => {
      result.current.addFiles(imageNames.map(n => makeFile(n)))
    })

    expect(result.current.attachments).toHaveLength(imageNames.length)
    result.current.attachments.forEach(a => {
      // pending OR uploading — eager upload may have already begun in the microtask
      expect(['pending', 'uploading', 'done']).toContain(a.status)
      expect(a.error).toBeUndefined()
    })
  })

  it('rejects SVG files', () => {
    const { result } = renderHook(() => useFileUpload('session-1'))

    act(() => {
      result.current.addFiles([makeFile('logo.svg')])
    })

    expect(result.current.attachments).toHaveLength(1)
    expect(result.current.attachments[0].status).toBe('error')
    expect(result.current.attachments[0].error).toContain('chat.fileUpload.unsupportedType')
  })

  it('rejects empty files', () => {
    const { result } = renderHook(() => useFileUpload('session-1'))

    act(() => {
      result.current.addFiles([makeFile('data.csv', 0)])
    })

    expect(result.current.attachments[0].status).toBe('error')
    expect(result.current.attachments[0].error).toBe('chat.fileUpload.emptyFile')
  })

  it('accepts existing text-based extensions', () => {
    const { result } = renderHook(() => useFileUpload('session-1'))
    const names = ['a.csv', 'b.xlsx', 'c.json', 'd.sql', 'e.txt', 'f.md', 'g.log']

    act(() => {
      result.current.addFiles(names.map(n => makeFile(n)))
    })

    result.current.attachments.forEach(a => {
      // Per eager-upload, status may already have moved past pending in the microtask
      expect(['pending', 'uploading', 'done']).toContain(a.status)
    })
  })

  it('uploadAll transitions pending → done with progress=100, leaving hasUploads=false', async () => {
    // BUG-0057 regression guard: previously the second setAttachments inside uploadAll
    // used `a === attachment` reference equality, but the first setAttachments replaced
    // the matching item with a new object (via `{ ...a, status: 'uploading' }`). The
    // reference comparison then never matched, so state was stuck at status='uploading'
    // / progress=0 → hasUploads=true forever → send button disabled.
    const { result } = renderHook(() => useFileUpload('session-1'))

    act(() => {
      result.current.addFiles([makeFile('a.png', 100, 'image/png')])
    })

    await act(async () => {
      await result.current.uploadAll()
    })

    expect(result.current.attachments[0].status).toBe('done')
    expect(result.current.attachments[0].progress).toBe(100)
    expect(result.current.attachments[0].response).toBeDefined()
    expect(result.current.hasUploads).toBe(false)
    expect(result.current.allDone).toBe(true)
  })

  it('removeAttachment removes the right item by id even after reorder/insert', () => {
    // BUG-0057 regression guard: removeAttachment must address by id, not reference.
    const { result } = renderHook(() => useFileUpload('session-1'))

    act(() => {
      result.current.addFiles([makeFile('a.png', 100, 'image/png'), makeFile('b.png', 100, 'image/png')])
    })
    const firstId = result.current.attachments[0].id

    act(() => {
      result.current.removeAttachment(firstId)
    })

    expect(result.current.attachments).toHaveLength(1)
    expect(result.current.attachments[0].file.name).toBe('b.png')
  })

  // --- Eager upload + abort + concurrency suite ---

  it('addFiles in microtask triggers uploadFile (eager upload)', async () => {
    const { result } = renderHook(() => useFileUpload('session-1'))

    act(() => {
      result.current.addFiles([makeFile('a.png', 100, 'image/png')])
    })

    await act(async () => {
      await flushMicrotasks()
    })

    await waitFor(() => {
      expect(mockUploadFile).toHaveBeenCalledTimes(1)
    })
    // Signal is the third argument (signature: uploadFile(file, sessionId, signal?))
    const callArgs = mockUploadFile.mock.calls[0]
    expect(callArgs[1]).toBe('session-1')
    expect(callArgs[2]).toBeInstanceOf(AbortSignal)
  })

  it('5 files addFiles → concurrent active ≤ 3 (chunked)', async () => {
    // Controlled-promise pattern: track inflight count to assert concurrency cap.
    let active = 0
    let peak = 0
    const resolvers: Array<(r: FileUploadResponse) => void> = []
    mockUploadFile.mockImplementation((_file, _sessionId, signal) => {
      active += 1
      peak = Math.max(peak, active)
      return new Promise<FileUploadResponse>((resolve, reject) => {
        const onAbort = () => {
          active -= 1
          reject(new DOMException('aborted', 'AbortError'))
        }
        signal?.addEventListener('abort', onAbort, { once: true })
        resolvers.push((r) => {
          active -= 1
          signal?.removeEventListener('abort', onAbort)
          resolve(r)
        })
      })
    })

    const { result } = renderHook(() => useFileUpload('session-1'))

    act(() => {
      result.current.addFiles([
        makeFile('a.png', 100, 'image/png'),
        makeFile('b.png', 100, 'image/png'),
        makeFile('c.png', 100, 'image/png'),
        makeFile('d.png', 100, 'image/png'),
        makeFile('e.png', 100, 'image/png'),
      ])
    })

    // Wait until exactly 3 concurrent fetches are inflight (first chunk).
    await waitFor(() => {
      expect(mockUploadFile).toHaveBeenCalledTimes(3)
    })
    expect(active).toBe(3)
    expect(peak).toBe(3)

    // Resolve first chunk → next chunk should pick up.
    await act(async () => {
      resolvers[0](makeResponse('f1'))
      resolvers[1](makeResponse('f2'))
      resolvers[2](makeResponse('f3'))
      await flushMicrotasks()
    })

    await waitFor(() => {
      expect(mockUploadFile).toHaveBeenCalledTimes(5)
    })
    // At any point concurrency stayed ≤ 3
    expect(peak).toBeLessThanOrEqual(3)

    // Drain remaining
    await act(async () => {
      resolvers[3](makeResponse('f4'))
      resolvers[4](makeResponse('f5'))
      await flushMicrotasks()
    })
  })

  it('removeAttachment during uploading aborts fetch and silent rejects', async () => {
    let capturedSignal: AbortSignal | undefined
    let rejectFetch!: (err: unknown) => void
    mockUploadFile.mockImplementation((_file, _sessionId, signal) => {
      capturedSignal = signal
      return new Promise<FileUploadResponse>((_resolve, reject) => {
        rejectFetch = reject
        signal?.addEventListener('abort', () => {
          reject(new DOMException('aborted', 'AbortError'))
        }, { once: true })
      })
    })

    const { result } = renderHook(() => useFileUpload('session-1'))

    act(() => {
      result.current.addFiles([makeFile('big.png', 5_000_000, 'image/png')])
    })

    await waitFor(() => {
      expect(mockUploadFile).toHaveBeenCalledTimes(1)
    })

    const id = result.current.attachments[0].id

    act(() => {
      result.current.removeAttachment(id)
    })

    expect(capturedSignal?.aborted).toBe(true)
    expect(result.current.attachments).toHaveLength(0)

    // Subsequent reject (the fetch path normally throws AbortError naturally) MUST NOT
    // crash, and the now-removed attachment MUST NOT reappear in state.
    await act(async () => {
      // Ensure manual reject is also silent (covers the path where fetch itself ignores signal)
      rejectFetch(new DOMException('aborted', 'AbortError'))
      await flushMicrotasks()
    })

    expect(result.current.attachments).toHaveLength(0)
  })

  it('removeAttachment after done excludes that file from uploadAll responses', async () => {
    // Both files resolve immediately. Remove the first after done; uploadAll
    // returns only the second's response.
    mockUploadFile.mockImplementation(async (file) => {
      // Use file name to disambiguate
      return makeResponse(file.name.replace('.png', ''))
    })

    const { result } = renderHook(() => useFileUpload('session-1'))

    act(() => {
      result.current.addFiles([
        makeFile('alpha.png', 100, 'image/png'),
        makeFile('beta.png', 100, 'image/png'),
      ])
    })

    // Wait for both to settle as done via eager upload
    await waitFor(() => {
      expect(result.current.attachments.every(a => a.status === 'done')).toBe(true)
    })

    const firstId = result.current.attachments[0].id
    act(() => {
      result.current.removeAttachment(firstId)
    })
    expect(result.current.attachments).toHaveLength(1)

    let responses: FileUploadResponse[] = []
    await act(async () => {
      responses = await result.current.uploadAll()
    })

    expect(responses).toHaveLength(1)
    expect(responses[0].fileId).toBe('beta')
  })
})
