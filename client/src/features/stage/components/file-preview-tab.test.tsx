import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { buildReadFilePreviewPayload } from '@/features/chat/components/tools/renderers/read-file-output'
import type { StageTab } from '@/stores/stage-store'
import { FilePreviewTab } from './file-preview-tab'

const editorHarness = vi.hoisted(() => ({
  lastProps: null as null | {
    value?: string
    language?: string
    defaultLanguage?: string
    options?: Record<string, unknown>
  },
}))

vi.mock('@monaco-editor/react', () => ({
  default: ({
    value,
    language,
    defaultLanguage,
    options,
  }: {
    value?: string
    language?: string
    defaultLanguage?: string
    options?: Record<string, unknown>
  }) => {
    editorHarness.lastProps = { value, language, defaultLanguage, options }
    return <div data-testid="file-preview-editor">{value}</div>
  },
}))

function buildTabFromRawOutput(rawOutput: string, overrides: Partial<StageTab> = {}) {
  const payload = buildReadFilePreviewPayload({
    partId: 'part-1',
    messageId: 'msg-1',
    output: rawOutput,
    metadata: { truncated: true },
  })

  expect(payload).not.toBeNull()
  if (!payload) return null

  return {
    tabId: 'file-preview-1',
    type: 'file_preview',
    title: payload.filename,
    originSessionId: 'sess-1',
    payload,
    createdAt: 0,
    ...overrides,
  } satisfies StageTab
}

describe('FilePreviewTab', () => {
  it('renders extracted content from parsed read output and hides wrapper tags', () => {
    const tab = buildTabFromRawOutput(
      [
        '<path>/workspace/src/app/example.ts</path>',
        '<type>file</type>',
        '<content>export const answer = 42\n</content>',
      ].join('\n'),
    )

    expect(tab).not.toBeNull()
    if (!tab) return

    render(<FilePreviewTab tab={tab} />)

    expect(screen.getByText('/workspace/src/app/example.ts')).toBeTruthy()
    expect(screen.getByText('export const answer = 42')).toBeTruthy()
    expect(screen.queryByText('<path>/workspace/src/app/example.ts</path>')).toBeNull()
    expect(screen.queryByText('<type>file</type>')).toBeNull()
    expect(screen.queryByText('<content>')).toBeNull()
    expect(screen.queryByText('</content>')).toBeNull()
    expect(editorHarness.lastProps).toEqual({
      value: 'export const answer = 42\n',
      language: 'typescript',
      defaultLanguage: undefined,
      options: expect.objectContaining({ readOnly: true }),
    })
    expect(screen.getByTestId('file-preview-editor')).toBeTruthy()
  })

  it('updates the Monaco language when the preview payload language changes', () => {
    const firstTab = buildTabFromRawOutput(
      [
        '<path>/workspace/src/app/example.ts</path>',
        '<type>file</type>',
        '<content>export const answer = 42\n</content>',
      ].join('\n'),
    )
    const secondTab = buildTabFromRawOutput(
      [
        '<path>/workspace/src/app/example.sql</path>',
        '<type>file</type>',
        '<content>select 1;\n</content>',
      ].join('\n'),
    )

    expect(firstTab).not.toBeNull()
    expect(secondTab).not.toBeNull()
    if (!firstTab || !secondTab) return

    const { rerender } = render(<FilePreviewTab tab={firstTab} />)
    expect(editorHarness.lastProps?.language).toBe('typescript')
    expect(editorHarness.lastProps?.defaultLanguage).toBeUndefined()

    rerender(<FilePreviewTab tab={secondTab} />)

    expect(editorHarness.lastProps).toEqual({
      value: 'select 1;\n',
      language: 'sql',
      defaultLanguage: undefined,
      options: expect.objectContaining({ readOnly: true }),
    })
  })

  it('does not render the activity rail inside the file preview tab', () => {
    const tab = buildTabFromRawOutput(
      [
        '<path>/workspace/src/app/example.ts</path>',
        '<type>file</type>',
        '<content>export const answer = 42\n</content>',
      ].join('\n'),
    )

    expect(tab).not.toBeNull()
    if (!tab) return

    render(<FilePreviewTab tab={tab} />)

    expect(screen.queryByTestId('stage-activity-rail')).toBeNull()
    expect(screen.queryByTestId('stage-activity-rail-stub')).toBeNull()
  })

  it('fails closed for malformed payloads', () => {
    const tab = {
      tabId: 'file-preview-invalid',
      type: 'file_preview',
      title: 'invalid',
      originSessionId: 'sess-1',
      payload: { language: 'typescript' },
      createdAt: 0,
    } satisfies StageTab

    render(<FilePreviewTab tab={tab} />)

    expect(screen.queryByTestId('file-preview-tab')).toBeNull()
    expect(screen.queryByTestId('file-preview-editor')).toBeNull()
  })
})
