import { describe, expect, it } from 'vitest'
import { buildReadFilePreviewPayload, inferFilePreviewLanguage, parseReadFileOutput } from '../renderers/read-file-output'

describe('read-file-output', () => {
  it('extracts path, type, and content from tagged output', () => {
    const result = parseReadFileOutput('<path>/tmp/docs/readme.md</path><type>file</type><content>Hello</content>')

    expect(result).toEqual({
      filePath: '/tmp/docs/readme.md',
      fileType: 'file',
      content: 'Hello',
    })
  })

  it('returns null when content tag is missing', () => {
    expect(parseReadFileOutput('<path>/tmp/docs/readme.md</path><type>file</type>')).toBeNull()
  })

  it('returns null when type is not file', () => {
    expect(parseReadFileOutput('<path>/tmp/docs/readme.md</path><type>markdown</type><content>Hello</content>')).toBeNull()
  })

  it('returns null when path is missing or empty', () => {
    expect(parseReadFileOutput('<type>file</type><content>Hello</content>')).toBeNull()
    expect(parseReadFileOutput('<path></path><type>file</type><content>Hello</content>')).toBeNull()
  })

  it('returns null when tags are out of order', () => {
    expect(parseReadFileOutput('<type>file</type><path>/tmp/docs/readme.md</path><content>Hello</content>')).toBeNull()
  })

  it('returns null when surrounded by extra text', () => {
    expect(parseReadFileOutput('prefix<path>/tmp/docs/readme.md</path><type>file</type><content>Hello</content>suffix')).toBeNull()
  })

  it('preserves original newlines in content', () => {
    const result = parseReadFileOutput('<path>/tmp/docs/readme.md</path><type>file</type><content>line1\nline2\r\nline3</content>')

    expect(result?.content).toBe('line1\nline2\r\nline3')
  })

  it('allows empty content and preserves it through payload building', () => {
    expect(parseReadFileOutput('<path>/tmp/docs/readme.md</path><type>file</type><content></content>')).toEqual({
      filePath: '/tmp/docs/readme.md',
      fileType: 'file',
      content: '',
    })

    expect(buildReadFilePreviewPayload({
      partId: 'part-4',
      messageId: 'msg-4',
      output: '<path>/tmp/docs/readme.md</path><type>file</type><content></content>',
      metadata: {},
    })).toEqual({
      sourceKey: 'msg-4:part-4',
      filePath: '/tmp/docs/readme.md',
      filename: 'readme.md',
      fileType: 'file',
      content: '',
      truncated: false,
      language: 'markdown',
    })
  })

  it('falls back to message and part ids when callID is missing', () => {
    const result = buildReadFilePreviewPayload({
      partId: 'part-7',
      messageId: 'msg-9',
      output: '<path>/tmp/docs/readme.md</path><type>file</type><content>Hello</content>',
      metadata: {},
    })

    expect(result?.sourceKey).toBe('msg-9:part-7')
  })

  it('maps common suffixes to preview languages', () => {
    expect(inferFilePreviewLanguage('notes.md')).toBe('markdown')
    expect(inferFilePreviewLanguage('data.json')).toBe('json')
    expect(inferFilePreviewLanguage('config.yml')).toBe('yaml')
    expect(inferFilePreviewLanguage('config.yaml')).toBe('yaml')
    expect(inferFilePreviewLanguage('script.ts')).toBe('typescript')
    expect(inferFilePreviewLanguage('script.tsx')).toBe('typescript')
    expect(inferFilePreviewLanguage('script.js')).toBe('javascript')
    expect(inferFilePreviewLanguage('script.jsx')).toBe('javascript')
    expect(inferFilePreviewLanguage('Main.java')).toBe('java')
    expect(inferFilePreviewLanguage('query.sql')).toBe('sql')
    expect(inferFilePreviewLanguage('deploy.sh')).toBe('shell')
    expect(inferFilePreviewLanguage('layout.xml')).toBe('xml')
    expect(inferFilePreviewLanguage('/TMP/Docs/README.MD')).toBe('markdown')
    expect(inferFilePreviewLanguage('settings.ini')).toBe('ini')
    expect(inferFilePreviewLanguage('app.properties')).toBe('ini')
    expect(inferFilePreviewLanguage('Cargo.toml')).toBe('toml')
    expect(inferFilePreviewLanguage('README')).toBe('plaintext')
  })

  it('builds a preview payload with basename-derived filename and truncation flag', () => {
    const result = buildReadFilePreviewPayload({
      partId: 'part-1',
      messageId: 'msg-1',
      callID: 'call-1',
      output: '<path>/tmp/docs/readme.md</path><type>file</type><content>Hello</content>',
      metadata: { truncated: true },
    })

    expect(result).toEqual({
      sourceKey: 'call-1',
      filePath: '/tmp/docs/readme.md',
      filename: 'readme.md',
      fileType: 'file',
      content: 'Hello',
      truncated: true,
      language: 'markdown',
    })
  })

  it('returns null when payload cannot be parsed', () => {
    expect(buildReadFilePreviewPayload({
      partId: 'part-3',
      messageId: 'msg-3',
      output: '<path>/tmp/docs/readme.md</path><type>file</type>',
      metadata: { truncated: true },
    })).toBeNull()
  })

  it('returns null when payload has a missing path or non-file type', () => {
    expect(buildReadFilePreviewPayload({
      partId: 'part-5',
      messageId: 'msg-5',
      output: '<type>file</type><content>Hello</content>',
      metadata: {},
    })).toBeNull()

    expect(buildReadFilePreviewPayload({
      partId: 'part-6',
      messageId: 'msg-6',
      output: '<path>/tmp/docs/readme.md</path><type>markdown</type><content>Hello</content>',
      metadata: {},
    })).toBeNull()
  })

  it('returns null when the approved path does not yield a filename', () => {
    expect(buildReadFilePreviewPayload({
      partId: 'part-8',
      messageId: 'msg-8',
      output: '<path>/</path><type>file</type><content>Hello</content>',
      metadata: {},
    })).toBeNull()
  })
})
