import { useEffect, useMemo, useRef, useState } from 'react'
import { FileTextIcon, NetworkIcon, FileSpreadsheetIcon, ScrollTextIcon, FileIcon, ChevronDownIcon, LayoutDashboardIcon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import { useSessionStore } from '@/stores/session-store'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import { Button } from '@/components/ui/button'
import type { FileArtifact, FileArtifactKind } from '@/services/api/file-artifacts'
import { FileArtifactStatusBadge } from './file-artifact-status-badge'

const KIND_ICON: Record<FileArtifactKind, typeof FileIcon> = {
  report: ScrollTextIcon,
  er_diagram: NetworkIcon,
  sql_script: FileTextIcon,
  dataset: FileSpreadsheetIcon,
  dashboard: LayoutDashboardIcon,
  other: FileIcon,
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function FilesTab() {
  const { t } = useI18n()
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const list = useFileArtifactsStore((s) => (activeSessionId ? s.bySessionId[activeSessionId] : undefined))
  const prevSessionIdRef = useRef<string | null>(null)

  useEffect(() => {
    if (activeSessionId && activeSessionId !== prevSessionIdRef.current) {
      prevSessionIdRef.current = activeSessionId
      void useFileArtifactsStore.getState().fetchForSession(activeSessionId)
    }
  }, [activeSessionId])

  const groups = useMemo(() => {
    if (!list) return null
    return {
      temporary: list.filter((f) => f.status === 'temporary'),
      candidate: list.filter((f) => f.status === 'candidate'),
    }
  }, [list])

  if (!activeSessionId) {
    return (
      <div className="flex h-full items-center justify-center bg-canvas px-6 text-sm text-muted">
        {t('files.empty.noSession')}
      </div>
    )
  }

  const totalCount = (groups?.temporary.length ?? 0) + (groups?.candidate.length ?? 0)
  if (totalCount === 0) {
    return (
      <div className="flex h-full items-center justify-center bg-canvas px-6 text-sm text-muted">
        {t('files.empty.noFiles')}
      </div>
    )
  }

  return (
    <div className="flex h-full w-full min-h-0 flex-col bg-canvas overflow-y-auto">
      <div className="px-4 py-3">
        {groups && groups.temporary.length > 0 && (
          <FileSection
            titleKey="files.section.temporary"
            count={groups.temporary.length}
            tone="muted"
          >
            {groups.temporary.map((file) => (
              <TemporaryFileRow key={file.id} file={file} />
            ))}
          </FileSection>
        )}
        {groups && groups.candidate.length > 0 && (
          <FileSection
            titleKey="files.section.candidates"
            count={groups.candidate.length}
            tone="warn"
          >
            {groups.candidate.map((file) => (
              <CandidateFileRow key={file.id} file={file} sessionId={activeSessionId} />
            ))}
          </FileSection>
        )}
      </div>
    </div>
  )
}

function FileSection({
  titleKey,
  count,
  tone,
  children,
}: {
  titleKey: string
  count: number
  tone: 'muted' | 'warn'
  children: React.ReactNode
}) {
  const { t } = useI18n()
  const [collapsed, setCollapsed] = useState(false)
  const dotClass = tone === 'warn' ? 'bg-accent-warn' : 'bg-text-muted'
  return (
    <section className="mb-4">
      <button
        type="button"
        className="mb-2 flex w-full items-center gap-2 text-xs font-medium text-muted hover:text-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focusRing"
        aria-expanded={!collapsed}
        onClick={() => setCollapsed((c) => !c)}
      >
        <ChevronDownIcon
          aria-hidden
          className={`h-3 w-3 transition-transform duration-fast ${collapsed ? '-rotate-90' : ''}`}
        />
        <span aria-hidden className={`h-1.5 w-1.5 rounded-full ${dotClass}`} />
        <span>{t(titleKey as never)}</span>
        <span className="text-soft">({count})</span>
      </button>
      {!collapsed && <div className="space-y-2">{children}</div>}
    </section>
  )
}

function TemporaryFileRow({ file }: { file: FileArtifact }) {
  const { t } = useI18n()
  const promote = useFileArtifactsStore((s) => s.promote)
  const discard = useFileArtifactsStore((s) => s.discard)
  const Icon = KIND_ICON[file.kind]
  return (
    <div className="rounded-md border border-subtle bg-panel px-3 py-2">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <Icon aria-hidden className="h-4 w-4 text-muted shrink-0" />
          <span className="truncate text-sm text-strong">{file.filename}</span>
        </div>
        <span className="text-xs text-muted shrink-0">{formatSize(file.sizeBytes)}</span>
      </div>
      <div className="mt-0.5 text-xs text-muted">
        {file.kind} · {new Date(file.createdAt).toLocaleString()}
      </div>
      <div className="mt-2 flex justify-end gap-1.5">
        <Button variant="ghost" size="sm" aria-label={t('files.action.open')}>
          {t('files.action.open')}
        </Button>
        <Button
          variant="ghost"
          size="sm"
          aria-label={t('files.action.markAsCandidate')}
          onClick={() => void promote(file.id)}
        >
          📌 {t('files.action.markAsCandidate')}
        </Button>
        <Button
          variant="ghost"
          size="sm"
          aria-label={t('files.action.discard')}
          onClick={() => void discard(file.id)}
        >
          {t('files.action.discard')}
        </Button>
      </div>
    </div>
  )
}

function CandidateFileRow({ file, sessionId }: { file: FileArtifact; sessionId: string }) {
  const { t } = useI18n()
  const archive = useFileArtifactsStore((s) => s.archive)
  const discard = useFileArtifactsStore((s) => s.discard)
  const Icon = KIND_ICON[file.kind]
  return (
    <div className="rounded-md border border-subtle border-l-2 border-l-accent-warn bg-status-warningSurface px-3 py-2">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <Icon aria-hidden className="h-4 w-4 text-strong shrink-0" />
          <span className="truncate text-sm text-strong">{file.filename}</span>
          <FileArtifactStatusBadge status="candidate" />
        </div>
        <span className="text-xs text-muted shrink-0">{formatSize(file.sizeBytes)}</span>
      </div>
      {(file.title || file.summary) && (
        <div className="mt-0.5 text-xs text-muted truncate">
          {[file.title, file.summary].filter(Boolean).join(' · ')}
        </div>
      )}
      <div className="mt-2 flex justify-end gap-1.5">
        <Button variant="ghost" size="sm" aria-label={t('files.action.open')}>
          {t('files.action.open')}
        </Button>
        <Button
          variant="default"
          size="sm"
          aria-label={t('files.action.archive')}
          onClick={() => void archive(sessionId, file.id)}
        >
          ✓ {t('files.action.archive')}
        </Button>
        <Button
          variant="ghost"
          size="sm"
          aria-label={t('files.action.discard')}
          onClick={() => void discard(file.id)}
        >
          {t('files.action.discard')}
        </Button>
      </div>
    </div>
  )
}
