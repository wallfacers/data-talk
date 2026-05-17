import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { BubbleAttachmentList } from './bubble-attachment-list'
import type { FileUploadPart } from '@/services/channel/types'

// jsdom does not implement URL.createObjectURL / revokeObjectURL.
// FilePreviewDialog may call these once it mounts (e.g. on a remote image fetch),
// so stub them at the URL constructor level to avoid runtime errors.
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

function makePart(overrides: Partial<FileUploadPart> = {}): FileUploadPart {
  return {
    type: 'file_upload',
    id: 'prt-' + Math.random().toString(36).slice(2, 8),
    sessionID: 's1',
    messageID: 'm1',
    fileId: 'fid-' + Math.random().toString(36).slice(2, 8),
    filename: 'a.png',
    mimeType: 'image/png',
    sizeBytes: 1024,
    analysis: {},
    ...overrides,
  } as FileUploadPart
}

describe('BubbleAttachmentList', () => {
  it('renders a single image chip with a thumbnail URL pointing at /api/files/{fileId}/content', () => {
    const part = makePart({
      id: 'p-1',
      fileId: 'file-abc',
      filename: 'photo.png',
      mimeType: 'image/png',
      sizeBytes: 2048,
    })

    const { container } = render(<BubbleAttachmentList parts={[part]} />)

    // Container exists
    expect(container.firstChild).not.toBeNull()

    // Exactly one chip
    const buttons = screen.getAllByRole('button')
    expect(buttons).toHaveLength(1)

    // Image chip uses <img> with src = getFileContentUrl(fileId)
    const img = container.querySelector('img') as HTMLImageElement | null
    expect(img).not.toBeNull()
    expect(img!.getAttribute('src')).toBe('/api/files/file-abc/content')
  })

  it('renders multiple chips horizontally in the same order as the parts array', () => {
    const parts: FileUploadPart[] = [
      makePart({ id: 'p-a', fileId: 'fid-a', filename: 'A.csv', mimeType: 'text/csv' }),
      makePart({ id: 'p-b', fileId: 'fid-b', filename: 'B.png', mimeType: 'image/png' }),
      makePart({ id: 'p-c', fileId: 'fid-c', filename: 'C.json', mimeType: 'application/json' }),
    ]

    render(<BubbleAttachmentList parts={parts} />)

    const buttons = screen.getAllByRole('button')
    expect(buttons).toHaveLength(3)
    // Order matches the input parts array
    expect(buttons[0].getAttribute('aria-label')).toBe('A.csv, 预览')
    expect(buttons[1].getAttribute('aria-label')).toBe('B.png, 预览')
    expect(buttons[2].getAttribute('aria-label')).toBe('C.json, 预览')
  })

  it('renders null (no container) when parts is empty', () => {
    const { container } = render(<BubbleAttachmentList parts={[]} />)
    expect(container.firstChild).toBeNull()
  })

  it('container layout className includes ml-auto / max-w-[85%] / flex / flex-row / gap-1.5 / overflow-x-auto / justify-end', () => {
    const { container } = render(
      <BubbleAttachmentList parts={[makePart()]} />,
    )
    const wrapper = container.firstChild as HTMLElement
    expect(wrapper).not.toBeNull()
    const cls = wrapper.className
    expect(cls).toContain('ml-auto')
    expect(cls).toContain('max-w-[85%]')
    expect(cls).toContain('flex')
    expect(cls).toContain('flex-row')
    expect(cls).toContain('gap-1.5')
    expect(cls).toContain('overflow-x-auto')
    expect(cls).toContain('justify-end')
  })

  it('clicking a chip opens the FilePreviewDialog with the file metadata in the header', async () => {
    // FilePreviewDialog will fetch /api/files/.../content for remote sources.
    // Stub fetch so the async load resolves without hitting the network.
    const blob = new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], { type: 'image/png' })
    const fetchMock = vi.fn(() => Promise.resolve(new Response(blob, { status: 200 })))
    vi.stubGlobal('fetch', fetchMock)

    const part = makePart({
      id: 'p-1',
      fileId: 'file-abc',
      filename: 'photo.png',
      mimeType: 'image/png',
      sizeBytes: 2048,
    })

    render(<BubbleAttachmentList parts={[part]} />)

    // Dialog is not open yet — no [role="dialog"] in the DOM
    expect(document.querySelector('[role="dialog"]')).toBeNull()

    fireEvent.click(screen.getByRole('button'))

    // Dialog now rendered (Radix renders into a portal under document.body)
    const dialog = await screen.findByRole('dialog')
    expect(dialog).not.toBeNull()
    // Header inside the dialog shows the clicked file's name
    expect(within(dialog).getByText('photo.png')).toBeInTheDocument()
  })

  it('renders a chip with missing fileId as disabled (aria-disabled=true) and a click does not open the dialog', () => {
    // Use a type assertion to bypass the strict FileUploadPart shape — in
    // practice legacy DB rows may have an empty string fileId.
    const part = makePart({
      id: 'p-legacy',
      fileId: '' as unknown as string,
      filename: 'legacy.png',
      mimeType: 'image/png',
    })

    render(<BubbleAttachmentList parts={[part]} />)

    const button = screen.getByRole('button') as HTMLButtonElement
    expect(button.getAttribute('aria-disabled')).toBe('true')
    expect(button.disabled).toBe(true)

    // Image thumbnail is suppressed when fileId is missing
    expect(document.querySelector('img')).toBeNull()

    fireEvent.click(button)
    // Dialog must not have opened
    expect(document.querySelector('[role="dialog"]')).toBeNull()
  })
})
