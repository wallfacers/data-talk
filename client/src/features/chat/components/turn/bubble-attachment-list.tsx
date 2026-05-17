import { useMemo, useState } from 'react'
import { FileChip } from '@/features/session/components/file-chip'
import { FilePreviewDialog, type PreviewSource } from '@/features/session/components/file-preview-dialog'
import { getFileContentUrl } from '@/services/api/file-upload'
import type { FileUploadPart } from '@/services/channel/types'

export interface BubbleAttachmentListProps {
  parts: FileUploadPart[]
}

/**
 * Renders the row of file attachment chips that floats above a user bubble.
 *
 * Layout rules (design.md Decision 5):
 * - `ml-auto` + `justify-end` → align to the right edge of the bubble column.
 * - `max-w-[85%]` mirrors the bubble's own max width so long chip lists never
 *   exceed the bubble footprint.
 * - `flex-row` + `overflow-x-auto` → chips lay out horizontally and the
 *   container scrolls horizontally when the total width exceeds the budget.
 * - `gap-1.5` matches the in-composer chip row spacing.
 *
 * Behavior:
 * - Empty `parts` array → renders `null` (spec.md Requirement 1: no container).
 * - Click on any chip → opens the shared `FilePreviewDialog` with a `remote`
 *   `PreviewSource` derived from the clicked part.
 * - Image chips use `getFileContentUrl(part.fileId)` as `<img src>` directly;
 *   we rely on the backend `Cache-Control: private, max-age=300` plus the
 *   browser's native HTTP cache instead of fetching into a blob.
 * - Defensive fallback: when `part.fileId` is missing (legacy DB rows from
 *   pre-fileId messages), the chip is rendered disabled with no thumbnail —
 *   it degrades to a grey, non-interactive icon chip.
 * - `onMouseDown` propagation is stopped at the container so press-and-drag
 *   inside the chip row does not bubble up and affect the parent bubble's
 *   hover/selection region (matches the prompt-composer chip-row pattern).
 */
export function BubbleAttachmentList({ parts }: BubbleAttachmentListProps) {
  const [previewOpen, setPreviewOpen] = useState(false)
  const [activePart, setActivePart] = useState<FileUploadPart | null>(null)

  if (parts.length === 0) return null

  const previewSource = useMemo<PreviewSource | null>(() => {
    if (!activePart?.fileId) return null
    return {
      kind: 'remote',
      fileId: activePart.fileId,
      filename: activePart.filename,
      mimeType: activePart.mimeType,
      sizeBytes: activePart.sizeBytes,
    }
  }, [activePart])

  return (
    <div
      className="ml-auto max-w-[85%] flex flex-row gap-1.5 overflow-x-auto justify-end pb-1"
      onMouseDown={(e) => e.stopPropagation()}
    >
      {parts.map((part) => {
        const hasFileId = Boolean(part.fileId)
        const isImage = part.mimeType?.startsWith('image/') ?? false
        return (
          <FileChip
            key={part.id}
            filename={part.filename}
            sizeBytes={part.sizeBytes}
            mimeType={part.mimeType}
            thumbnailUrl={isImage && hasFileId ? getFileContentUrl(part.fileId) : undefined}
            onClick={() => {
              setActivePart(part)
              setPreviewOpen(true)
            }}
            disabled={!hasFileId}
            ariaLabel={`${part.filename}, 预览`}
          />
        )
      })}
      <FilePreviewDialog
        source={previewSource}
        open={previewOpen}
        onOpenChange={(next) => {
          setPreviewOpen(next)
          // Intentionally do NOT clear activePart on close — the dialog runs a
          // close animation and clearing `source` mid-animation would cause a
          // visual content swap before the dialog finishes fading out.
        }}
      />
    </div>
  )
}
