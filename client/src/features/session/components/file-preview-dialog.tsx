import { useEffect, useState, useMemo, useCallback, type JSX } from 'react'
import { FileText, FileSpreadsheet, FileJson, ImageIcon, Maximize2, Minimize2, XIcon } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Markdown } from '@/features/chat/components/markdown/markdown'
import type { FileAttachment } from '../useFileUpload'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'

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

function readFileAsText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(reader.error)
    reader.readAsText(file)
  })
}

export function FilePreviewDialog({
  attachment,
  open,
  onOpenChange,
}: {
  attachment: FileAttachment | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useI18n()
  const [textContent, setTextContent] = useState<string | null>(null)
  const [readError, setReadError] = useState<string | null>(null)
  const [maximized, setMaximized] = useState(false)

  const file = attachment?.file ?? null
  const filename = file?.name ?? ''

  const fileTypeLabel = useMemo(() => {
    const ext = getExt(filename)
    if (IMAGE_EXTENSIONS.has(ext)) return t('chat.filePreview.image')
    if (MARKDOWN_EXTENSIONS.has(ext)) return t('chat.filePreview.markdown')
    if (CODE_EXTENSIONS.has(ext)) return t('chat.filePreview.code')
    return t('chat.filePreview.file')
  }, [filename, t])

  const [imageUrl, setImageUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!file || !isImage(filename)) {
      setImageUrl(null)
      return
    }
    const url = URL.createObjectURL(file)
    setImageUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [file, filename])

  const loadText = useCallback(async () => {
    if (!file || isImage(filename)) return
    try {
      const text = await readFileAsText(file)
      setTextContent(text)
      setReadError(null)
    } catch (err) {
      setReadError(String(err))
    }
  }, [file, filename])

  useEffect(() => {
    if (open && file && !isImage(filename)) {
      loadText()
    }
    if (!open) {
      setTextContent(null)
      setReadError(null)
    }
  }, [open, file, filename, loadText])

  if (!file) return null

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
            {fileTypeLabel} · {formatSize(file.size)}
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
              {t('chat.filePreview.readError', { error: readError })}
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
