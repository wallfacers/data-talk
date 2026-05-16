import { describe, expect, it, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'

// Mock the uploadFile API
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

function makeFile(name: string, size = 1024, type = 'application/octet-stream'): File {
  const file = new File(['x'.repeat(size)], name, { type })
  return file
}

describe('useFileUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('accepts image extensions (.png, .jpg, .jpeg, .gif, .webp, .bmp)', () => {
    const { result } = renderHook(() => useFileUpload('session-1'))
    const imageNames = ['a.png', 'b.jpg', 'c.jpeg', 'd.gif', 'e.webp', 'f.bmp']

    act(() => {
      result.current.addFiles(imageNames.map(n => makeFile(n)))
    })

    expect(result.current.attachments).toHaveLength(imageNames.length)
    result.current.attachments.forEach(a => {
      expect(a.status).toBe('pending')
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
      expect(a.status).toBe('pending')
    })
  })
})
