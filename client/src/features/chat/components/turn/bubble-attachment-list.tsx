import { useMemo, useState } from 'react'
import { FileChip } from '@/features/session/components/file-chip'
import { FilePreviewDialog, type PreviewSource } from '@/features/session/components/file-preview-dialog'
import { getFileContentUrl } from '@/services/api/file-upload'
import type { FilePart, FileUploadPart, Part } from '@/services/channel/types'
import { useI18n } from '@/i18n/use-i18n'

export interface BubbleAttachmentListProps {
  /**
   * Accepts the message's full parts array. The component picks the
   * attachments it knows how to render — both legacy `file_upload` parts
   * (CSV/JSON/SQL/image-without-dataUri) and image `file` parts echoed back
   * by OpenCode on the new batch-image path.
   */
  parts: ReadonlyArray<Part | FileUploadPart>
}

interface NormalizedAttachment {
  key: string
  filename: string
  mimeType: string
  sizeBytes: number
  isImage: boolean
  // `<img src>` for image thumbnail rendering. Either a GET endpoint URL
  // (legacy file_upload) or a data URI (new FilePart path). Undefined for
  // non-image attachments (which render an icon instead).
  thumbnailSrc?: string
  // Preview source consumed by FilePreviewDialog on chip click. `null` means
  // no usable source (legacy row missing fileId, or echoed FilePart with the
  // url field stripped) — chip is rendered disabled.
  previewSource: PreviewSource | null
}

function pickAttachments(parts: ReadonlyArray<Part | FileUploadPart>): NormalizedAttachment[] {
  const out: NormalizedAttachment[] = []
  for (const p of parts) {
    if (p.type === 'file_upload') {
      const fu = p as FileUploadPart
      const hasFileId = Boolean(fu.fileId)
      const isImage = fu.mimeType?.startsWith('image/') ?? false
      const thumbnailSrc = isImage && hasFileId ? getFileContentUrl(fu.fileId) : undefined
      const previewSource: PreviewSource | null = hasFileId
        ? {
            kind: 'remote',
            fileId: fu.fileId,
            filename: fu.filename,
            mimeType: fu.mimeType,
            sizeBytes: fu.sizeBytes,
          }
        : null
      out.push({
        key: fu.id,
        filename: fu.filename,
        mimeType: fu.mimeType,
        sizeBytes: fu.sizeBytes,
        isImage,
        thumbnailSrc,
        previewSource,
      })
      continue
    }
    if (p.type === 'file') {
      const fp = p as FilePart & { mime?: string; filename?: string }
      const mime = (fp as { mime?: string }).mime ?? 'application/octet-stream'
      if (!mime.startsWith('image/')) continue // only image FileParts surface as chips
      const url = fp.url ?? ''
      const hasUrl = url.length > 0
      const filename = fp.filename ?? ''
      // base64 → byte estimate: each 4 chars encode 3 bytes. Good enough for
      // a header label like "PNG · 142.6 KB".
      const sizeBytes = hasUrl ? Math.floor((url.length * 3) / 4) : 0
      const previewSource: PreviewSource | null = hasUrl
        ? { kind: 'embedded', dataUri: url, filename, mimeType: mime, sizeBytes }
        : null
      out.push({
        key: fp.id,
        filename,
        mimeType: mime,
        sizeBytes,
        isImage: true,
        thumbnailSrc: hasUrl ? url : undefined,
        previewSource,
      })
    }
  }
  return out
}

/**
 * Renders the row of file attachment chips that floats above a user bubble.
 *
 * Two data sources are accepted, and visually they MUST be indistinguishable:
 * - `file_upload` parts (legacy): chip thumbnail is `/api/files/{fileId}/content`,
 *   click opens FilePreviewDialog with a `remote` PreviewSource.
 * - `file` parts with `mime` starting with `image/` (new batch-image path):
 *   chip thumbnail is the embedded data URI; click opens FilePreviewDialog
 *   with an `embedded` PreviewSource that renders the data URI directly.
 *
 * Layout rules (design.md Decision 5) are unchanged: ml-auto, max-w-[85%],
 * gap-1.5, flex-row, horizontal scroll when overflowing.
 */
export function BubbleAttachmentList({ parts }: BubbleAttachmentListProps) {
  const { t } = useI18n()
  const [previewOpen, setPreviewOpen] = useState(false)
  const [activeKey, setActiveKey] = useState<string | null>(null)

  const attachments = useMemo(() => pickAttachments(parts), [parts])

  const previewSource = useMemo<PreviewSource | null>(() => {
    if (!activeKey) return null
    const match = attachments.find((a) => a.key === activeKey)
    return match?.previewSource ?? null
  }, [activeKey, attachments])

  if (attachments.length === 0) return null

  return (
    <div
      className="ml-auto max-w-[85%] flex flex-row gap-1.5 overflow-x-auto justify-end pb-1"
      onMouseDown={(e) => e.stopPropagation()}
    >
      {attachments.map((a) => (
        <FileChip
          key={a.key}
          filename={a.filename || t('chat.image.unavailable')}
          sizeBytes={a.sizeBytes}
          mimeType={a.mimeType}
          thumbnailUrl={a.thumbnailSrc}
          onClick={() => {
            setActiveKey(a.key)
            setPreviewOpen(true)
          }}
          disabled={a.previewSource === null}
          ariaLabel={`${a.filename || t('chat.image.unavailable')}, 预览`}
        />
      ))}
      <FilePreviewDialog
        source={previewSource}
        open={previewOpen}
        onOpenChange={(next) => {
          setPreviewOpen(next)
          // Intentionally do NOT clear activeKey on close — the dialog runs a
          // close animation and clearing `source` mid-animation would cause a
          // visual content swap before the dialog finishes fading out.
        }}
      />
    </div>
  )
}
