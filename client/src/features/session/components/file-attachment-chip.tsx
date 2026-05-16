import { FileText, FileSpreadsheet, FileJson, XIcon } from 'lucide-react'
import { useMemo, useState } from 'react'
import type { FileAttachment } from '../useFileUpload'
import { cn } from '@/lib/utils'
import { FilePreviewDialog } from './file-preview-dialog'
import { useI18n } from '@/i18n/use-i18n'

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const IMAGE_EXTENSIONS = new Set(['.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp'])

function isImageFile(filename: string): boolean {
  const ext = '.' + filename.split('.').pop()?.toLowerCase()
  return IMAGE_EXTENSIONS.has(ext)
}

function fileIcon(filename: string) {
  const ext = filename.split('.').pop()?.toLowerCase()
  switch (ext) {
    case 'csv': case 'xlsx': case 'xls':
      return <FileSpreadsheet className="h-4 w-4 shrink-0" />
    case 'json': case 'jsonl':
      return <FileJson className="h-4 w-4 shrink-0" />
    default:
      return <FileText className="h-4 w-4 shrink-0" />
  }
}

export function FileAttachmentChip({
  attachment,
  onRemove,
}: {
  attachment: FileAttachment
  onRemove: () => void
}) {
  const { file, status, progress, error } = attachment
  const isUploading = status === 'uploading'
  const isError = status === 'error'
  const isImage = isImageFile(file.name)
  const [previewOpen, setPreviewOpen] = useState(false)
  const { t } = useI18n()

  const thumbnailUrl = useMemo(() => {
    if (!isImage) return null
    return URL.createObjectURL(file)
  }, [isImage, file])

  return (
    <>
    <div
      className={cn(
        'group flex items-center gap-1.5 rounded-lg border px-2 py-1 text-xs transition-colors max-w-[180px] shrink-0',
        isError
          ? 'border-status-danger/40 bg-status-dangerSurface text-status-danger'
          : 'border-border-default bg-bg-soft text-text-base hover:bg-bg-subtle cursor-pointer',
      )}
      onClick={(e) => {
        e.stopPropagation()
        if (!isUploading && !isError) setPreviewOpen(true)
      }}
    >
      {isImage && thumbnailUrl ? (
        <img src={thumbnailUrl} alt="" className="h-6 w-6 shrink-0 rounded object-cover" />
      ) : (
        <span className="shrink-0 text-text-muted">{fileIcon(file.name)}</span>
      )}

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1">
          <span className="truncate font-medium">{file.name}</span>
          <span className="shrink-0 text-text-muted">{formatSize(file.size)}</span>
        </div>

        {isUploading && (
          <div className="mt-0.5 h-0.5 w-full overflow-hidden rounded-full bg-bg-subtle">
            <div
              className="h-full rounded-full bg-accent-primary transition-all duration-200"
              style={{ width: `${progress}%` }}
            />
          </div>
        )}

        {isError && error && (
          <span className="mt-0.5 block truncate text-[10px] text-status-danger">{error}</span>
        )}
      </div>

      {isUploading && (
        <span className="shrink-0 tabular-nums text-text-muted text-[10px]">{progress}%</span>
      )}

      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation()
          onRemove()
        }}
        className={cn(
          'shrink-0 rounded p-0.5 transition-colors',
          isError
            ? 'text-status-danger hover:bg-status-danger/20'
            : 'text-text-muted hover:bg-bg-subtle hover:text-text-base',
        )}
        aria-label={t('chat.removeFile')}
      >
        <XIcon className="h-3 w-3" />
      </button>
    </div>
    <FilePreviewDialog
      attachment={attachment}
      open={previewOpen}
      onOpenChange={setPreviewOpen}
    />
    </>
  )
}
