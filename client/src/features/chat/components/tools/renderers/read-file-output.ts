export type FilePreviewPayload = {
  sourceKey: string
  filePath: string | null
  filename: string
  fileType: string
  content: string
  truncated: boolean
  language: string
}

type ReadFileOutputInput = {
  partId: string
  messageId: string
  callID?: string
  output: string
  metadata?: {
    truncated?: boolean
  }
}

const FILE_LANGUAGE_BY_EXTENSION: Record<string, string> = {
  '.md': 'markdown',
  '.json': 'json',
  '.yml': 'yaml',
  '.yaml': 'yaml',
  '.js': 'javascript',
  '.jsx': 'javascript',
  '.ts': 'typescript',
  '.tsx': 'typescript',
  '.java': 'java',
  '.sql': 'sql',
  '.sh': 'shell',
  '.xml': 'xml',
  '.ini': 'ini',
  '.properties': 'ini',
  '.toml': 'toml',
}

function basename(pathLike: string | null): string | null {
  if (!pathLike) return null
  const normalized = pathLike.replaceAll('\\', '/')
  const parts = normalized.split('/').filter(Boolean)
  return parts.length > 0 ? parts[parts.length - 1] : null
}

function inferLanguageFromBasename(name: string): string {
  const lower = name.toLowerCase()
  const index = lower.lastIndexOf('.')
  if (index <= 0) return 'plaintext'
  return FILE_LANGUAGE_BY_EXTENSION[lower.slice(index)] ?? 'plaintext'
}

export function parseReadFileOutput(raw: string): { filePath: string | null; fileType: string; content: string } | null {
  const match = raw.match(/^\s*<path>([\s\S]*?)<\/path>\s*<type>file<\/type>\s*<content>([\s\S]*?)<\/content>\s*$/)
  if (!match) return null

  const filePath = match[1]
  const content = match[2]
  if (!filePath || filePath.trim().length === 0) return null

  return { filePath, fileType: 'file', content }
}

export function inferFilePreviewLanguage(filenameOrPath: string | null): string {
  const name = basename(filenameOrPath)
  return name ? inferLanguageFromBasename(name) : 'plaintext'
}

export function buildReadFilePreviewPayload(input: ReadFileOutputInput): FilePreviewPayload | null {
  if (typeof input.output !== 'string') return null

  const parsed = parseReadFileOutput(input.output)
  if (!parsed) return null

  const filename = basename(parsed.filePath)
  if (!filename) return null

  return {
    sourceKey: input.callID ?? `${input.messageId}:${input.partId}`,
    filePath: parsed.filePath,
    filename,
    fileType: parsed.fileType,
    content: parsed.content,
    truncated: input.metadata?.truncated === true,
    language: inferFilePreviewLanguage(parsed.filePath ?? filename),
  }
}
