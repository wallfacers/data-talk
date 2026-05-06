import { useMemo } from 'react'
import { FileIcon, FileSpreadsheetIcon, FileTextIcon, NetworkIcon, ScrollTextIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import { FileArtifactStatusBadge } from '@/features/stage/components/file-artifact-status-badge'
import { generateUuid } from '@/lib/uuid'
import type { ToolRendererProps } from '../tool-registry'
import type { FileArtifact, FileArtifactKind } from '@/services/api/file-artifacts'

const KIND_ICON: Record<FileArtifactKind, typeof FileIcon> = {
  report: ScrollTextIcon,
  er_diagram: NetworkIcon,
  sql_script: FileTextIcon,
  dataset: FileSpreadsheetIcon,
  other: FileIcon,
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

type ArchiveOutput = {
  fileArtifactId?: string
  filename?: string
  kind?: FileArtifactKind
  sizeBytes?: number
}

function findArtifactInStore(fileArtifactId: string): FileArtifact | null {
  const state = useFileArtifactsStore.getState()
  for (const list of Object.values(state.bySessionId)) {
    const hit = list.find((f) => f.id === fileArtifactId)
    if (hit) return hit
  }
  for (const list of Object.values(state.byConnectionId)) {
    const hit = list.find((f) => f.id === fileArtifactId)
    if (hit) return hit
  }
  return null
}

export function DatatalkArchiveArtifact({ part }: ToolRendererProps) {
  const { t } = useI18n()
  const sessionId = part.sessionID
  const output = (part.state.output as ArchiveOutput | undefined) ?? {}
  const input = (part.state.input ?? {}) as { path?: string; kind?: FileArtifactKind }

  const fileArtifactId = output.fileArtifactId ?? null
  const live = useFileArtifactsStore((s) => {
    if (!fileArtifactId) return null
    for (const list of Object.values(s.bySessionId)) {
      const hit = list.find((f) => f.id === fileArtifactId)
      if (hit) return hit
    }
    for (const list of Object.values(s.byConnectionId)) {
      const hit = list.find((f) => f.id === fileArtifactId)
      if (hit) return hit
    }
    return null
  })
  const archive = useFileArtifactsStore((s) => s.archive)
  const discard = useFileArtifactsStore((s) => s.discard)

  const fallback = useMemo<FileArtifact | null>(() => {
    if (!fileArtifactId) return null
    return (
      findArtifactInStore(fileArtifactId) ?? {
        id: fileArtifactId,
        scope: 'session',
        status: 'candidate',
        kind: output.kind ?? input.kind ?? 'other',
        sessionId,
        connectionId: null,
        filename: output.filename ?? input.path ?? fileArtifactId,
        physicalPath: '',
        sizeBytes: output.sizeBytes ?? 0,
        mimeType: null,
        title: null,
        summary: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        archivedAt: null,
        metadata: {},
      }
    )
  }, [fileArtifactId, input.kind, input.path, output.filename, output.kind, output.sizeBytes, sessionId])

  const file = live ?? fallback
  if (!file) return null
  const Icon = KIND_ICON[file.kind]

  const handleViewInStage = () => {
    const stage = useStageStore.getState()
    const existing = stage.tabs.find((tab) => tab.type === 'files')
    if (existing) {
      stage.focusTab(existing.tabId)
    } else {
      const tab: StageTab = {
        tabId: generateUuid(),
        type: 'files',
        title: t('files.tabs.session'),
        originSessionId: sessionId,
        payload: null,
        createdAt: Date.now(),
      }
      stage.openTab(tab)
    }
    stage.openStage()
  }

  return (
    <div
      data-testid="chat-file-artifact-card"
      className="rounded-md border border-subtle bg-panel px-3 py-2"
    >
      <div className="flex items-center gap-2">
        <Icon aria-hidden className="h-4 w-4 text-muted shrink-0" />
        <span className="truncate text-sm font-medium text-strong">{file.filename}</span>
        <FileArtifactStatusBadge status={file.status} />
      </div>
      <div className="mt-0.5 text-xs text-muted">
        {file.kind} · {formatSize(file.sizeBytes)}
        {file.summary ? ` · ${file.summary}` : ''}
      </div>
      <div className="mt-2 flex justify-end gap-1.5">
        <Button
          variant="ghost"
          size="sm"
          aria-label={t('files.chatCard.viewInStage')}
          onClick={handleViewInStage}
        >
          {t('files.chatCard.viewInStage')}
        </Button>
        {file.status === 'candidate' && (
          <Button
            variant="default"
            size="sm"
            aria-label={t('files.chatCard.archiveNow')}
            onClick={() => void archive(sessionId, file.id)}
          >
            {t('files.chatCard.archiveNow')}
          </Button>
        )}
        {file.status !== 'archived' && (
          <Button
            variant="ghost"
            size="sm"
            aria-label={t('files.action.discard')}
            onClick={() => void discard(file.id)}
          >
            {t('files.action.discard')}
          </Button>
        )}
      </div>
    </div>
  )
}
