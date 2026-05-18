import { FileText, FileSpreadsheet, FileJson, ImageIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface FileChipProps {
  filename: string
  sizeBytes: number
  mimeType: string
  thumbnailUrl?: string
  onClick?: () => void
  disabled?: boolean
  ariaLabel?: string
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'])
const SPREADSHEET_EXTENSIONS = new Set(['csv', 'xlsx', 'xls'])
const JSON_EXTENSIONS = new Set(['json', 'jsonl'])

function getExt(filename: string): string {
  return filename.split('.').pop()?.toLowerCase() ?? ''
}

function getFileIcon(filename: string, mimeType: string) {
  if (mimeType.startsWith('image/')) {
    return <ImageIcon className="h-6 w-6 shrink-0" />
  }
  const ext = getExt(filename)
  if (IMAGE_EXTENSIONS.has(ext)) {
    return <ImageIcon className="h-6 w-6 shrink-0" />
  }
  if (SPREADSHEET_EXTENSIONS.has(ext)) {
    return <FileSpreadsheet className="h-6 w-6 shrink-0" />
  }
  if (JSON_EXTENSIONS.has(ext)) {
    return <FileJson className="h-6 w-6 shrink-0" />
  }
  return <FileText className="h-6 w-6 shrink-0" />
}

export function FileChip({
  filename,
  sizeBytes,
  mimeType,
  thumbnailUrl,
  onClick,
  disabled,
  ariaLabel,
}: FileChipProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={ariaLabel ?? `${filename}, 预览`}
      aria-disabled={disabled || undefined}
      className={cn(
        'group flex items-center gap-1.5 rounded-lg border px-2 py-1 text-xs transition-colors max-w-[180px] shrink-0',
        'border-border-default bg-bg-soft text-text-base',
        !disabled && 'cursor-pointer hover:bg-bg-subtle hover:border-border-strong',
        !disabled &&
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent-primary/40 focus-visible:ring-offset-1 focus-visible:ring-offset-bg-canvas',
        !disabled && 'active:bg-accent-primary/8 active:border-accent-primary/40',
        disabled && 'opacity-60 cursor-not-allowed',
      )}
    >
      {thumbnailUrl ? (
        <img src={thumbnailUrl} alt="" className="h-6 w-6 shrink-0 rounded object-cover" />
      ) : (
        <span className="shrink-0 text-text-muted">{getFileIcon(filename, mimeType)}</span>
      )}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1">
          <span className="truncate font-medium">{filename}</span>
          <span className="shrink-0 text-text-muted">{formatSize(sizeBytes)}</span>
        </div>
      </div>
    </button>
  )
}
