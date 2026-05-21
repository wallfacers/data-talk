import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import { FilePreviewDialog, type PreviewSource } from './file-preview-dialog'

// jsdom does not implement URL.createObjectURL / revokeObjectURL.
// Stub these globally on the URL constructor (vi.stubGlobal('URL', ...) wipes
// non-trivial members like URL.parse and breaks unrelated code paths).
let createObjectURLSpy: ReturnType<typeof vi.fn>
let revokeObjectURLSpy: ReturnType<typeof vi.fn>
const originalCreate = (URL as unknown as { createObjectURL?: typeof URL.createObjectURL }).createObjectURL
const originalRevoke = (URL as unknown as { revokeObjectURL?: typeof URL.revokeObjectURL }).revokeObjectURL

beforeEach(() => {
  createObjectURLSpy = vi.fn(() => 'blob:mock-url')
  revokeObjectURLSpy = vi.fn()
  ;(URL as unknown as { createObjectURL: typeof URL.createObjectURL }).createObjectURL =
    createObjectURLSpy as unknown as typeof URL.createObjectURL
  ;(URL as unknown as { revokeObjectURL: typeof URL.revokeObjectURL }).revokeObjectURL =
    revokeObjectURLSpy as unknown as typeof URL.revokeObjectURL
})

afterEach(() => {
  cleanup()
  if (originalCreate) {
    ;(URL as unknown as { createObjectURL: typeof URL.createObjectURL }).createObjectURL = originalCreate
  } else {
    delete (URL as unknown as { createObjectURL?: typeof URL.createObjectURL }).createObjectURL
  }
  if (originalRevoke) {
    ;(URL as unknown as { revokeObjectURL: typeof URL.revokeObjectURL }).revokeObjectURL = originalRevoke
  } else {
    delete (URL as unknown as { revokeObjectURL?: typeof URL.revokeObjectURL }).revokeObjectURL
  }
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

function makeLocalImageSource(): PreviewSource {
  const file = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'a.png', { type: 'image/png' })
  return { kind: 'local', file }
}

function makeLocalTextSource(content = 'hello world'): PreviewSource {
  const file = new File([content], 'note.txt', { type: 'text/plain' })
  // jsdom File.text() may be missing or unreliable depending on version — pin it.
  Object.defineProperty(file, 'text', {
    value: () => Promise.resolve(content),
    writable: true,
  })
  return { kind: 'local', file }
}

function makeRemoteImageSource(): PreviewSource {
  return {
    kind: 'remote',
    fileId: 'file-123',
    filename: 'photo.jpg',
    mimeType: 'image/jpeg',
    sizeBytes: 4096,
  }
}

describe('FilePreviewDialog', () => {
  it('returns null when source is null', () => {
    const { container } = render(
      <FilePreviewDialog source={null} open={true} onOpenChange={() => {}} />,
    )
    expect(container.firstChild).toBeNull()
  })

  it('renders a blob: <img> for a local image source', async () => {
    render(
      <FilePreviewDialog
        source={makeLocalImageSource()}
        open={true}
        onOpenChange={() => {}}
      />,
    )

    // Header shows the local file's name
    expect(await screen.findByText('a.png')).toBeInTheDocument()

    const img = await waitFor(() => {
      const found = document.querySelector('img[alt="a.png"]') as HTMLImageElement | null
      expect(found).not.toBeNull()
      return found!
    })
    expect(img.getAttribute('src')).toMatch(/^blob:/)
    expect(createObjectURLSpy).toHaveBeenCalledTimes(1)
  })

  it('renders the file content for a local text source', async () => {
    render(
      <FilePreviewDialog
        source={makeLocalTextSource('local-text-body')}
        open={true}
        onOpenChange={() => {}}
      />,
    )

    expect(await screen.findByText('note.txt')).toBeInTheDocument()
    expect(await screen.findByText('local-text-body')).toBeInTheDocument()
    // Size formatted ("15 B" for "local-text-body" = 15 chars)
    expect(screen.getByText(/15 B/)).toBeInTheDocument()
  })

  it('renders an <img> for a successful remote image fetch', async () => {
    const blob = new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], { type: 'image/png' })
    const fetchMock = vi.fn(() => Promise.resolve(new Response(blob, { status: 200 })))
    vi.stubGlobal('fetch', fetchMock)

    render(
      <FilePreviewDialog
        source={makeRemoteImageSource()}
        open={true}
        onOpenChange={() => {}}
      />,
    )

    expect(await screen.findByText('photo.jpg')).toBeInTheDocument()

    const img = await waitFor(() => {
      const found = document.querySelector('img[alt="photo.jpg"]') as HTMLImageElement | null
      expect(found).not.toBeNull()
      return found!
    })
    expect(img.getAttribute('src')).toMatch(/^blob:/)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/files/file-123/content'))
    expect(createObjectURLSpy).toHaveBeenCalledTimes(1)
  })

  it('shows the friendly readError text when remote fetch returns 404', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(null, { status: 404 })))
    vi.stubGlobal('fetch', fetchMock)

    render(
      <FilePreviewDialog
        source={makeRemoteImageSource()}
        open={true}
        onOpenChange={() => {}}
      />,
    )

    // Error rendered via t('chat.filePreview.readError', { error: 'HTTP 404' })
    // zh-CN: "文件读取失败：HTTP 404"
    expect(await screen.findByText(/HTTP 404/)).toBeInTheDocument()
    expect(screen.getByText(/文件读取失败/)).toBeInTheDocument()
  })

  it('shows the friendly readError text when remote fetch rejects', async () => {
    const fetchMock = vi.fn(() => Promise.reject(new Error('boom-net-down')))
    vi.stubGlobal('fetch', fetchMock)

    render(
      <FilePreviewDialog
        source={makeRemoteImageSource()}
        open={true}
        onOpenChange={() => {}}
      />,
    )

    expect(await screen.findByText(/boom-net-down/)).toBeInTheDocument()
  })

  it('calls URL.revokeObjectURL on unmount after a local image source was loaded', async () => {
    const { unmount } = render(
      <FilePreviewDialog
        source={makeLocalImageSource()}
        open={true}
        onOpenChange={() => {}}
      />,
    )

    // Wait for the image to render so the objectURL is definitely created
    await waitFor(() => {
      expect(document.querySelector('img[alt="a.png"]')).not.toBeNull()
    })
    expect(createObjectURLSpy).toHaveBeenCalledTimes(1)

    act(() => {
      unmount()
    })

    expect(revokeObjectURLSpy).toHaveBeenCalledTimes(1)
    expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:mock-url')
  })
})
