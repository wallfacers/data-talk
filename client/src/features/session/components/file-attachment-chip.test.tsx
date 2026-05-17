import { describe, expect, it, vi, beforeAll } from 'vitest'
import { render, screen } from '@testing-library/react'
import { FileAttachmentChip } from './file-attachment-chip'
import type { FileAttachment } from '../useFileUpload'

// jsdom does not implement URL.createObjectURL
beforeAll(() => {
  if (typeof URL.createObjectURL === 'undefined') {
    URL.createObjectURL = vi.fn(() => 'blob:mock-url')
  }
  if (typeof URL.revokeObjectURL === 'undefined') {
    URL.revokeObjectURL = vi.fn()
  }
})

function makeAttachment(overrides: Partial<FileAttachment> = {}): FileAttachment {
  return {
    id: 'att-test',
    file: new File(['x'.repeat(1024)], 'test.csv', { type: 'text/csv' }),
    status: 'pending',
    progress: 0,
    ...overrides,
  }
}

describe('FileAttachmentChip', () => {
  it('renders file icon for non-image files', () => {
    const attachment = makeAttachment()
    render(<FileAttachmentChip attachment={attachment} onRemove={() => {}} />)

    // Should render filename
    expect(screen.getByText('test.csv')).toBeInTheDocument()
    // Should NOT render an img element
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('renders thumbnail for image files', () => {
    const imageFile = new File(['x'.repeat(1024)], 'photo.png', { type: 'image/png' })
    const attachment = makeAttachment({ file: imageFile })
    render(<FileAttachmentChip attachment={attachment} onRemove={() => {}} />)

    // Should render an img element with object-cover for thumbnails
    const img = document.querySelector('img')
    expect(img).toBeInTheDocument()
    expect(img?.className).toContain('object-cover')
  })

  it('shows error state', () => {
    const attachment = makeAttachment({ status: 'error', error: 'Upload failed' })
    render(<FileAttachmentChip attachment={attachment} onRemove={() => {}} />)

    expect(screen.getByText('Upload failed')).toBeInTheDocument()
  })
})
