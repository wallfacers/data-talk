import { XIcon } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { FileAttachment } from '../useFileUpload'
import { cn } from '@/lib/utils'
import { FileChip } from './file-chip'
import { FilePreviewDialog } from './file-preview-dialog'
import { useI18n } from '@/i18n/use-i18n'

const IMAGE_EXTENSIONS = new Set(['.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp'])

function isImageFile(filename: string): boolean {
  const ext = '.' + filename.split('.').pop()?.toLowerCase()
  return IMAGE_EXTENSIONS.has(ext)
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

  const [thumbnailUrl, setThumbnailUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!isImage) {
      setThumbnailUrl(null)
      return
    }
    const url = URL.createObjectURL(file)
    setThumbnailUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [isImage, file])

  return (
    <>
      <div
        className={cn(
          'group flex flex-col gap-0.5 shrink-0',
          // Error state: subtle ring around the wrapper so FileChip itself stays neutral.
          isError && 'rounded-lg p-0.5 ring-1 ring-status-danger/40',
        )}
      >
        <div className="flex items-center gap-1">
          <FileChip
            filename={file.name}
            sizeBytes={file.size}
            mimeType={file.type}
            thumbnailUrl={thumbnailUrl ?? undefined}
            onClick={isUploading || isError ? undefined : () => setPreviewOpen(true)}
            disabled={isUploading || isError}
            ariaLabel={`${file.name}, 预览`}
          />
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
          {isUploading && (
            <span className="shrink-0 tabular-nums text-text-muted text-[10px]">{progress}%</span>
          )}
        </div>

        {isUploading && (
          <div className="h-0.5 w-full overflow-hidden rounded-full bg-bg-subtle">
            <div
              className="h-full rounded-full bg-accent-primary transition-all duration-200"
              style={{ width: `${progress}%` }}
            />
          </div>
        )}

        {isError && error && (
          <span className="block truncate text-[10px] text-status-danger">{error}</span>
        )}
      </div>
      <FilePreviewDialog
        source={previewOpen ? { kind: 'local', file: attachment.file } : null}
        open={previewOpen}
        onOpenChange={setPreviewOpen}
      />
    </>
  )
}
