import { useState, useRef, useCallback, type ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'

const ALLOWED_EXTENSIONS = new Set(['csv', 'xlsx', 'xls', 'json', 'jsonl', 'sql', 'txt', 'md', 'log', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'])
const MAX_SIZE_BYTES = 50 * 1024 * 1024 // 50 MB

function filterFiles(fileList: FileList): File[] {
  return Array.from(fileList).filter((file) => {
    // Extension-less files (no dot, or a trailing dot) pass the type gate here —
    // the backend content-sniffs them on upload. Only enforce the whitelist when
    // a usable extension is present.
    const dotIdx = file.name.lastIndexOf('.')
    const hasExt = dotIdx >= 0 && dotIdx < file.name.length - 1
    if (hasExt) {
      const ext = file.name.slice(dotIdx + 1).toLowerCase()
      if (!ALLOWED_EXTENSIONS.has(ext)) return false
    }
    if (file.size === 0) return false
    if (file.size > MAX_SIZE_BYTES) return false
    return true
  })
}

export function FileDropZone({
  onFiles,
  children,
}: {
  onFiles: (files: FileList) => void
  children: ReactNode
}) {
  const [isDragging, setIsDragging] = useState(false)
  const { t } = useI18n()
  const dragCounter = useRef(0)

  const handleDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    dragCounter.current += 1
    if (dragCounter.current === 1) {
      setIsDragging(true)
    }
  }, [])

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    dragCounter.current -= 1
    if (dragCounter.current === 0) {
      setIsDragging(false)
    }
  }, [])

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
  }, [])

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      dragCounter.current = 0
      setIsDragging(false)

      const files = e.dataTransfer.files
      if (files.length === 0) return

      // Filter to allowed types, but pass full FileList so the hook can show
      // errors for rejected files. We wrap the filtered list.
      const filtered = filterFiles(files)
      if (filtered.length === 0) return

      // Create a FileList-like structure from filtered files
      // Since FileList is not constructable, we pass the original and let
      // the hook handle validation messages per-file.
      onFiles(files)
    },
    [onFiles],
  )

  return (
    <div
      className="relative"
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      {children}
      {isDragging && (
        <div
          className={cn(
            'absolute inset-0 z-10 flex items-center justify-center rounded-2xl',
            'border-2 border-dashed border-accent-primary bg-accent-primary-surface/60',
            'pointer-events-none transition-opacity',
          )}
        >
          <span className="text-sm font-medium text-accent-primary">
            {t('chat.dropFilesHere')}
          </span>
        </div>
      )}
    </div>
  )
}
