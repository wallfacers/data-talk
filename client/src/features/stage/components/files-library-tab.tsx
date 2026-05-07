import { useEffect, useMemo, useRef, useState } from 'react'
import { FileIcon, FileSpreadsheetIcon, FileTextIcon, NetworkIcon, ScrollTextIcon, SearchIcon } from 'lucide-react'
import { toast } from 'sonner'
import { useI18n } from '@/i18n/use-i18n'
import { useConnectionStore } from '@/features/connection/store'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { FileArtifact, FileArtifactKind } from '@/services/api/file-artifacts'
import { FileArtifactStatusBadge } from './file-artifact-status-badge'

const KIND_ICON: Record<FileArtifactKind, typeof FileIcon> = {
  report: ScrollTextIcon,
  er_diagram: NetworkIcon,
  sql_script: FileTextIcon,
  dataset: FileSpreadsheetIcon,
  other: FileIcon,
}

const KIND_ORDER: FileArtifactKind[] = ['er_diagram', 'report', 'sql_script', 'dataset', 'other']

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function matchesSearch(file: FileArtifact, query: string): boolean {
  if (!query) return true
  const haystack = `${file.filename} ${file.title ?? ''} ${file.summary ?? ''}`.toLowerCase()
  return haystack.includes(query.toLowerCase())
}

export function FilesLibraryTab() {
  const { t } = useI18n()
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const list = useFileArtifactsStore((s) =>
    activeConnectionId ? s.byConnectionId[activeConnectionId] : undefined,
  )
  const [query, setQuery] = useState('')
  const [kindFilter, setKindFilter] = useState<FileArtifactKind | 'all'>('all')
  const prevConnectionIdRef = useRef<string | null>(null)

  useEffect(() => {
    if (activeConnectionId && activeConnectionId !== prevConnectionIdRef.current) {
      prevConnectionIdRef.current = activeConnectionId
      void useFileArtifactsStore.getState().fetchForConnection(activeConnectionId)
    }
  }, [activeConnectionId])

  const groups = useMemo(() => {
    if (!list) return null
    const result = {} as Record<FileArtifactKind, FileArtifact[]>
    for (const kind of KIND_ORDER) {
      result[kind] = []
    }
    for (const file of list) {
      if (file.status !== 'archived') continue
      result[file.kind].push(file)
    }
    return result
  }, [list])

  const filteredGroups = useMemo(() => {
    if (!groups) return null
    const next = {} as Record<FileArtifactKind, FileArtifact[]>
    for (const kind of KIND_ORDER) {
      if (kindFilter !== 'all' && kindFilter !== kind) {
        next[kind] = []
        continue
      }
      next[kind] = groups[kind].filter((f) => matchesSearch(f, query))
    }
    return next
  }, [groups, query, kindFilter])

  if (!activeConnectionId) {
    return (
      <div className="flex h-full items-center justify-center bg-canvas px-6 text-sm text-muted">
        {t('files.empty.noConnection')}
      </div>
    )
  }

  const totalArchived = groups
    ? KIND_ORDER.reduce((acc, kind) => acc + groups[kind].length, 0)
    : 0
  if (totalArchived === 0) {
    return (
      <div className="flex h-full items-center justify-center bg-canvas px-6 text-sm text-muted">
        {t('files.empty.noArchived')}
      </div>
    )
  }

  return (
    <div className="flex h-full w-full min-h-0 flex-col bg-canvas">
      <div className="flex items-center gap-2 border-b border-subtle bg-subtle px-4 py-2">
        <div className="relative flex-1 max-w-md">
          <SearchIcon
            aria-hidden
            className="pointer-events-none absolute left-2 top-1/2 h-4 w-4 -translate-y-1/2 text-soft"
          />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('files.library.search.placeholder')}
            aria-label={t('files.library.search.placeholder')}
            className="h-8 w-full rounded-md border border-subtle bg-panel pl-8 pr-2 text-sm text-base placeholder:text-soft hover:border-default focus:border-default focus:outline-2 focus:outline-offset-1 focus:outline-focusRing disabled:bg-subtle disabled:text-disabled"
          />
        </div>
        <Select
          value={kindFilter}
          onValueChange={(value) => setKindFilter(value as FileArtifactKind | 'all')}
        >
          <SelectTrigger
            className="h-8 w-44 bg-panel text-sm"
            aria-label={t('files.library.filter.label')}
          >
            <SelectValue placeholder={t('files.library.filter.kind.all')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('files.library.filter.kind.all')}</SelectItem>
            {KIND_ORDER.map((kind) => (
              <SelectItem key={kind} value={kind}>
                {t(`files.library.filter.kind.${kind}` as never)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        {filteredGroups &&
          KIND_ORDER.filter((kind) => filteredGroups[kind].length > 0).map((kind) => (
            <LibrarySection key={kind} kind={kind} files={filteredGroups[kind]} />
          ))}
      </div>
    </div>
  )
}

function LibrarySection({ kind, files }: { kind: FileArtifactKind; files: FileArtifact[] }) {
  const { t } = useI18n()
  return (
    <section className="mb-5">
      <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-muted">
        {t(`files.library.section.${kind}` as never)} ({files.length})
      </h3>
      <div className="space-y-2">
        {files.map((file) => (
          <ArchivedRow key={file.id} file={file} />
        ))}
      </div>
    </section>
  )
}

function ArchivedRow({ file }: { file: FileArtifact }) {
  const { t } = useI18n()
  const Icon = KIND_ICON[file.kind]
  const discard = useFileArtifactsStore((s) => s.discard)
  const fromSessionLabel = file.sessionId
    ? t('files.library.fromSession', { title: file.sessionId })
    : t('files.library.fromSessionDeleted')

  const handleCopyPath = async () => {
    try {
      await navigator.clipboard.writeText(file.physicalPath)
      toast.success(t('files.library.copyPathOk'))
    } catch {
      toast.error(t('files.library.copyPathFail'))
    }
  }

  return (
    <div className="rounded-md border border-subtle bg-panel px-3 py-2">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <Icon aria-hidden className="h-4 w-4 text-muted shrink-0" />
          <span className="truncate text-sm text-strong">{file.filename}</span>
          <FileArtifactStatusBadge status="archived" />
        </div>
        <span className="text-xs text-muted shrink-0">{formatSize(file.sizeBytes)}</span>
      </div>
      <div className="mt-0.5 flex flex-wrap items-center gap-x-3 text-xs text-muted">
        <span>
          {file.archivedAt
            ? t('files.library.archivedAt', { date: new Date(file.archivedAt).toLocaleDateString() })
            : null}
        </span>
        <span className={file.sessionId ? 'text-info' : 'text-soft'}>{fromSessionLabel}</span>
      </div>
      <div className="mt-2 flex justify-end gap-1.5">
        <Button variant="ghost" size="sm" aria-label={t('files.action.open')} disabled>
          {t('files.action.open')}
        </Button>
        <Button
          variant="ghost"
          size="sm"
          aria-label={t('files.action.copyPath')}
          onClick={handleCopyPath}
        >
          {t('files.action.copyPath')}
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
