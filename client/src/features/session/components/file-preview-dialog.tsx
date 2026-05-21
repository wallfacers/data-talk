import { useEffect, useMemo, useState, type JSX } from 'react'
import { FileText, FileSpreadsheet, FileJson, ImageIcon, Maximize2, Minimize2, XIcon } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Markdown } from '@/features/chat/components/markdown/markdown'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import { getFileContentUrl } from '@/services/api/file-upload'

/**
 * Preview data source for FilePreviewDialog.
 *
 * - `local`: an in-memory File (e.g. from PromptComposer before upload)
 * - `remote`: a server-side uploaded file referenced by fileId; bytes are
 *   fetched from `/api/files/{fileId}/content`
 * - `embedded`: a base64 data URI embedded in a message part (echoed back by
 *   OpenCode on the new batch-image path); rendered directly, no network I/O
 */
export type PreviewSource =
  | { kind: 'local'; file: File }
  | {
      kind: 'remote'
      fileId: string
      filename: string
      mimeType: string
      sizeBytes: number
    }
  | {
      kind: 'embedded'
      dataUri: string
      filename: string
      mimeType: string
      sizeBytes: number
    }

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const IMAGE_EXTENSIONS = new Set(['.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp'])
const MARKDOWN_EXTENSIONS = new Set(['.md', '.markdown'])
const CODE_EXTENSIONS = new Set(['.sql', '.json', '.jsonl', '.csv', '.log', '.txt'])

function getExt(filename: string): string {
  return '.' + filename.split('.').pop()?.toLowerCase()
}

function isImage(filename: string): boolean {
  return IMAGE_EXTENSIONS.has(getExt(filename))
}

function isMarkdown(filename: string): boolean {
  return MARKDOWN_EXTENSIONS.has(getExt(filename))
}

function isCode(filename: string): boolean {
  return CODE_EXTENSIONS.has(getExt(filename))
}

function fileTypeIcon(filename: string): JSX.Element {
  const ext = getExt(filename)
  if (IMAGE_EXTENSIONS.has(ext)) return <ImageIcon className="h-4 w-4" />
  if (['.csv', '.xlsx', '.xls'].includes(ext)) return <FileSpreadsheet className="h-4 w-4" />
  if (['.json', '.jsonl'].includes(ext)) return <FileJson className="h-4 w-4" />
  return <FileText className="h-4 w-4" />
}

export function FilePreviewDialog({
  source,
  open,
  onOpenChange,
}: {
  source: PreviewSource | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useI18n()
  const [textContent, setTextContent] = useState<string | null>(null)
  const [readError, setReadError] = useState<string | null>(null)
  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const [maximized, setMaximized] = useState(false)

  // Pull display fields uniformly from source regardless of kind.
  const filename = source?.kind === 'local'
    ? source.file.name
    : source && (source.kind === 'remote' || source.kind === 'embedded')
      ? source.filename
      : ''
  const sizeBytes = source?.kind === 'local'
    ? source.file.size
    : source && (source.kind === 'remote' || source.kind === 'embedded')
      ? source.sizeBytes
      : 0

  const fileTypeLabel = useMemo(() => {
    const ext = getExt(filename)
    if (IMAGE_EXTENSIONS.has(ext)) return t('chat.filePreview.image')
    if (MARKDOWN_EXTENSIONS.has(ext)) return t('chat.filePreview.markdown')
    if (CODE_EXTENSIONS.has(ext)) return t('chat.filePreview.code')
    return t('chat.filePreview.file')
  }, [filename, t])

  // Stable identity for useEffect: compare by primitive value (fileId / dataUri)
  // or File reference instead of the source object itself, so parent re-renders
  // during streaming do not trigger a re-fetch / image flash.
  const sourceKey = source?.kind === 'remote' ? source.fileId
    : source?.kind === 'local' ? source.file
    : source?.kind === 'embedded' ? source.dataUri : null

  // Unified byte loader: dispatches on source.kind, normalizes cleanup
  // (revoke any objectURL that was created locally OR from a remote blob).
  // NOTE: `open` is intentionally excluded from deps so the blob URL survives
  // the Dialog's close animation.  Content stays visible during fade-out;
  // cleanup happens when sourceKey changes or the component unmounts.
  useEffect(() => {
    if (!source) {
      setTextContent(null)
      setReadError(null)
      setImageUrl(null)
      return
    }

    let cancelled = false
    let createdObjectUrl: string | null = null
    const showImage = isImage(filename)

    // Reset per-load before kicking off async work
    setTextContent(null)
    setReadError(null)
    setImageUrl(null)

    const handleError = (err: unknown) => {
      if (cancelled) return
      const msg = err instanceof Error ? err.message : String(err)
      setReadError(msg)
    }

    if (source.kind === 'embedded') {
      // dataUri can be consumed directly by <img src>; no fetch, no
      // createObjectURL, no revoke. An empty dataUri means OpenCode lost the
      // url field for this historical message — surface as a friendly error.
      if (!source.dataUri) {
        setReadError('image_unavailable')
      } else if (showImage) {
        setImageUrl(source.dataUri)
      } else {
        // Non-image embedded payload is not produced by current flows, but
        // future-proof: try to decode as text if mime suggests it.
        setReadError('unsupported_embedded_mime')
      }
    } else if (source.kind === 'local') {
      if (showImage) {
        const url = URL.createObjectURL(source.file)
        createdObjectUrl = url
        setImageUrl(url)
      } else {
        // Prefer File.text() when available (modern browsers + jsdom); fall
        // back to FileReader for older environments.
        const readPromise =
          typeof source.file.text === 'function'
            ? source.file.text()
            : new Promise<string>((resolve, reject) => {
                const reader = new FileReader()
                reader.onload = () => resolve(reader.result as string)
                reader.onerror = () => reject(reader.error)
                reader.readAsText(source.file)
              })
        readPromise
          .then((text) => {
            if (!cancelled) setTextContent(text)
          })
          .catch(handleError)
      }
    } else {
      // remote: fetch /api/files/{fileId}/content
      const url = getFileContentUrl(source.fileId)
      fetch(url)
        .then(async (response) => {
          if (!response.ok) {
            throw new Error(`HTTP ${response.status}`)
          }
          if (showImage) {
            const blob = await response.blob()
            if (cancelled) return
            const objectUrl = URL.createObjectURL(blob)
            createdObjectUrl = objectUrl
            setImageUrl(objectUrl)
          } else {
            const text = await response.text()
            if (cancelled) return
            setTextContent(text)
          }
        })
        .catch(handleError)
    }

    return () => {
      cancelled = true
      if (createdObjectUrl) {
        URL.revokeObjectURL(createdObjectUrl)
      }
    }
  }, [sourceKey, filename])

  if (!source) return null

  const showImage = isImage(filename)
  const showMarkdown = isMarkdown(filename)
  const showCode = isCode(filename)

  return (
    <Dialog open={open} onOpenChange={(next) => {
      if (!next) setMaximized(false)
      onOpenChange(next)
    }}>
      <DialogContent
        showCloseButton={false}
        className={cn(
          'flex flex-col gap-0 overflow-hidden p-0',
          maximized
            ? 'max-h-[90vh] max-w-[90vw]'
            : 'max-h-[80vh] max-w-3xl',
        )}
      >
        {/* Header */}
        <div className="relative flex items-center border-b px-4 py-3">
          <span className="text-text-muted mr-2">{fileTypeIcon(filename)}</span>
          <DialogTitle className="text-sm font-medium truncate">
            {filename}
          </DialogTitle>
          <span className="absolute left-1/2 -translate-x-1/2 whitespace-nowrap text-xs text-text-muted">
            {fileTypeLabel} · {formatSize(sizeBytes)}
          </span>
          <div className="ml-auto flex items-center gap-0.5">
            <button
              type="button"
              className="rounded-sm p-1.5 text-text-muted transition-colors hover:bg-bg-subtle hover:text-text-base"
              onClick={() => setMaximized((v) => !v)}
              aria-label={maximized ? t('stage.restore') : t('stage.maximize')}
            >
              {maximized ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
            </button>
            <button
              type="button"
              className="rounded-sm p-1.5 text-text-muted transition-colors hover:bg-bg-subtle hover:text-text-base"
              onClick={() => {
                setMaximized(false)
                onOpenChange(false)
              }}
              aria-label={t('common.close')}
            >
              <XIcon className="h-4 w-4" />
            </button>
          </div>
        </div>
        <DialogDescription className="sr-only">
          {t('chat.filePreview.title', { filename })}
        </DialogDescription>

        {/* Body */}
        <div className={cn(
          'flex-1 min-h-0',
          showImage ? 'flex flex-col overflow-hidden' : 'overflow-auto',
        )}>
          {showImage && imageUrl && (
            <div className="flex flex-1 items-center justify-center p-4 min-h-0">
              <img
                src={imageUrl}
                alt={filename}
                className="max-h-full max-w-full rounded-md object-contain"
              />
            </div>
          )}

          {showMarkdown && textContent !== null && (
            <div className="p-4">
              <Markdown text={textContent} variant="plain" />
            </div>
          )}

          {showCode && textContent !== null && (
            <div className="p-4">
              <pre className={cn(
                'overflow-auto rounded-lg bg-bg-subtle p-3 text-xs leading-relaxed',
                'font-mono text-text-base whitespace-pre-wrap break-all',
              )}>
                <code>{textContent}</code>
              </pre>
            </div>
          )}

          {!showImage && !showMarkdown && !showCode && textContent !== null && (
            <div className="p-4">
              <pre className="overflow-auto rounded-lg bg-bg-subtle p-3 text-xs leading-relaxed font-mono text-text-base whitespace-pre-wrap">
                {textContent}
              </pre>
            </div>
          )}

          {readError && (
            <div className="p-4 text-sm text-status-danger">
              {t('chat.filePreview.readError', {
                error: readError === 'image_unavailable'
                  ? t('chat.image.unavailable')
                  : readError,
              })}
            </div>
          )}

          {!showImage && textContent === null && !readError && (
            <div className="flex items-center justify-center py-12 text-sm text-text-muted">
              {t('chat.filePreview.loading')}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
