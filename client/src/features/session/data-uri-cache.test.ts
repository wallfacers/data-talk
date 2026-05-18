import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'

// Mock fetchFileContent before importing the module under test. Each test
// resets the mock implementation; the imported helpers operate against the
// module-level cache, which we also clear via evictDataUri.
vi.mock('@/services/api/file-upload', () => ({
  fetchFileContent: vi.fn(),
  uploadFile: vi.fn(),
  getFileContentUrl: (id: string) => `/api/files/${id}/content`,
  deleteUploadedFile: vi.fn(),
}))

import { fetchFileContent } from '@/services/api/file-upload'
import { evictDataUri, getDataUri, prefetchDataUri } from './useFileUpload'

const mockFetch = vi.mocked(fetchFileContent)

function makeBlob(text = 'fake-bytes', type = 'image/png'): Blob {
  return new Blob([text], { type })
}

describe('image data URI cache', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    // Reset any cache entries seeded by individual tests so they don't leak
    // across the suite. We only know the test fileIds; evict each explicitly.
    evictDataUri('img-1')
    evictDataUri('img-2')
    evictDataUri('img-fail')
    evictDataUri('img-slow')
  })

  it('prefetchDataUri fetches once and caches the Promise across callers', async () => {
    mockFetch.mockResolvedValue(makeBlob())
    const a = prefetchDataUri('img-1')
    const b = prefetchDataUri('img-1')
    expect(a).toBe(b) // same Promise reference — caching works
    await a
    expect(mockFetch).toHaveBeenCalledTimes(1)
  })

  it('getDataUri returns the resolved data URI when prefetch already succeeded', async () => {
    mockFetch.mockResolvedValue(makeBlob('hello'))
    await prefetchDataUri('img-1')
    const uri = await getDataUri('img-1')
    expect(uri).toMatch(/^data:image\/png;base64,/)
  })

  it('getDataUri triggers prefetch when no entry exists yet', async () => {
    mockFetch.mockResolvedValue(makeBlob('xyz'))
    const uri = await getDataUri('img-2')
    expect(uri).toMatch(/^data:image\/png;base64,/)
    expect(mockFetch).toHaveBeenCalledWith('img-2')
  })

  it('getDataUri returns null when prefetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('boom'))
    const uri = await getDataUri('img-fail')
    expect(uri).toBeNull()
  })

  it('getDataUri returns null when the timeout elapses', async () => {
    // Never-resolving fetch
    mockFetch.mockReturnValue(new Promise(() => {}))
    const uri = await getDataUri('img-slow', 10)
    expect(uri).toBeNull()
  })

  it('evictDataUri removes the cached entry so subsequent prefetch re-fetches', async () => {
    mockFetch.mockResolvedValue(makeBlob())
    await prefetchDataUri('img-1')
    evictDataUri('img-1')
    await prefetchDataUri('img-1')
    expect(mockFetch).toHaveBeenCalledTimes(2)
  })
})
