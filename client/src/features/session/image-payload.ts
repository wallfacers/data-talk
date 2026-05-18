import type { Part } from '@/services/channel/types'
import {
  IMAGE_PAYLOAD_HARD_LIMIT_BYTES,
  IMAGE_PAYLOAD_WARN_BYTES,
} from './constants'

type AnyPart = Part | (Record<string, unknown> & { type: string })

function isImageFileUploadPart(p: AnyPart): p is AnyPart & { url: string; mimeType: string } {
  if (!p || typeof p !== 'object') return false
  if (p.type !== 'file_upload') return false
  const mime = (p as { mimeType?: unknown }).mimeType
  const url = (p as { url?: unknown }).url
  return typeof mime === 'string' && mime.startsWith('image/') && typeof url === 'string' && url.length > 0
}

/**
 * Sum the UTF-16 char length (~= byte length for base64 ASCII data URIs) of
 * every image file_upload part's `url` field. Non-image and url-less parts
 * contribute nothing.
 */
export function computeImagePayloadSize(parts: ReadonlyArray<unknown>): number {
  let total = 0
  for (const p of parts) {
    if (isImageFileUploadPart(p as AnyPart)) {
      total += (p as { url: string }).url.length
    }
  }
  return total
}

export type ImagePayloadVerdict =
  | { kind: 'ok'; bytes: number }
  | { kind: 'warn'; bytes: number }
  | { kind: 'reject'; bytes: number }

export function classifyImagePayload(bytes: number): ImagePayloadVerdict {
  if (bytes > IMAGE_PAYLOAD_HARD_LIMIT_BYTES) return { kind: 'reject', bytes }
  if (bytes > IMAGE_PAYLOAD_WARN_BYTES) return { kind: 'warn', bytes }
  return { kind: 'ok', bytes }
}
