import type { SqlExecuteResultItem } from '@/services/api/sql'
import { undoDml } from '@/services/api/sql'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useI18n } from '@/i18n/use-i18n'
import { RotateCcw } from 'lucide-react'
import { useState, useCallback } from 'react'
import { useSqlWorkbenchStore } from '@/features/stage/stores/sql-workbench-store'
import { formatSql } from '@/features/stage/utils/format-sql'
import { copyToClipboard } from '@/lib/utils'
import { toast } from 'sonner'

type SqlDmlSummaryPanelProps = {
  result: SqlExecuteResultItem
  tabId: string
  connectionKind?: string | null
}

export function SqlDmlSummaryPanel({ result, tabId, connectionKind }: SqlDmlSummaryPanelProps) {
  const { t } = useI18n()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [inverseSql, setInverseSql] = useState<string | null>(null)
  const [undoing, setUndoing] = useState(false)
  const [sqlDetailOpen, setSqlDetailOpen] = useState(false)
  const [sqlFormatted, setSqlFormatted] = useState(false)
  const setUndoConfirming = useSqlWorkbenchStore((s) => s.setUndoConfirming)
  const setUndoResult = useSqlWorkbenchStore((s) => s.setUndoResult)
  const undoState = useSqlWorkbenchStore(
    (s) => s.tabsById[tabId]?.undoStates?.[result.resultId],
  )

  const isUndone = undoState?.status === 'undone'

  const handleUndoClick = useCallback(async () => {
    if (!result.undoLogId) return
    try {
      const res = await undoDml({ undoLogId: result.undoLogId, confirmed: false })
      if (res.status === 'requires_confirmation') {
        setInverseSql(res.inverseSql)
        setUndoConfirming(tabId, result.resultId, res.inverseSql)
        setConfirmOpen(true)
      }
    } catch {
      toast.error(t('stage.queryEditor.undo.failed', { error: t('stage.queryEditor.confirmFailed') }))
    }
  }, [result.undoLogId, result.resultId, tabId, setUndoConfirming, t])

  const handleConfirmUndo = useCallback(async () => {
    if (!result.undoLogId) return
    setUndoing(true)
    try {
      const res = await undoDml({ undoLogId: result.undoLogId, confirmed: true, riskAck: 'L2' })
      if (res.status === 'undone') {
        setUndoResult(tabId, result.resultId, 'undone')
        setConfirmOpen(false)
        toast.success(t('stage.queryEditor.undo.success'))
      } else {
        setUndoResult(tabId, result.resultId, 'error', res.status)
        setConfirmOpen(false)
        toast.error(t('stage.queryEditor.undo.failed', { error: res.status }))
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : t('stage.queryEditor.confirmFailed')
      setUndoResult(tabId, result.resultId, 'error', msg)
      setConfirmOpen(false)
      toast.error(t('stage.queryEditor.undo.failed', { error: msg }))
    } finally {
      setUndoing(false)
    }
  }, [result.undoLogId, result.resultId, tabId, setUndoResult, t])

  const tableName = result.statementText?.match(/\b(?:FROM|INTO|UPDATE)\s+(\w+)/i)?.[1] ?? 'unknown'

  return (
    <div className="h-full overflow-y-auto">
      <Table className="w-full table-fixed text-xs">
        <TableHeader className="sticky top-0 bg-muted/40">
          <TableRow className="hover:bg-transparent">
            <TableHead className="h-8 w-10 border-b border-border/50 px-2 text-center">
              {t('stage.queryEditor.result.rowNumber')}
            </TableHead>
            <TableHead className="h-8 w-24 border-b border-border/50 px-2">
              {t('stage.queryEditor.result.action')}
            </TableHead>
            <TableHead className="h-8 w-20 border-b border-border/50 px-2">
              {t('stage.queryEditor.result.affectedRows')}
            </TableHead>
            <TableHead className="h-8 w-16 border-b border-border/50 px-2">
              {t('stage.queryEditor.result.duration')}
            </TableHead>
            <TableHead className="h-8 border-b border-border/50 px-2">
              {t('stage.queryEditor.result.sql')}
            </TableHead>
            <TableHead className="h-8 w-24 border-b border-border/50 px-2" />
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow className="border-b border-border/30">
            <TableCell className="px-2 py-1.5 text-center text-muted-foreground">1</TableCell>
            <TableCell className="px-2 py-1.5">
              {isUndone ? (
                <span className="text-muted-foreground">{result.title} ({t('stage.queryEditor.undo.reverted')})</span>
              ) : (
                result.title
              )}
            </TableCell>
            <TableCell className="px-2 py-1.5">{result.affectedRows ?? 0}</TableCell>
            <TableCell className="px-2 py-1.5">{result.executionMs}ms</TableCell>
            <TableCell
              className="cursor-pointer truncate px-2 py-1.5 text-muted-foreground hover:text-foreground"
              onClick={() => setSqlDetailOpen(true)}
            >
              {result.statementText}
            </TableCell>
            <TableCell className="px-2 py-1.5">
              <div className="flex items-center gap-2">
                {result.undoable && !isUndone && (
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-6 gap-1 text-xs"
                    onClick={handleUndoClick}
                  >
                    <RotateCcw className="h-3 w-3" />
                    {t('stage.queryEditor.undo.button')}
                  </Button>
                )}
                {isUndone && (
                  <span className="text-xs text-muted-foreground">{t('stage.queryEditor.undo.reverted')}</span>
                )}
              </div>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>

      <Dialog open={sqlDetailOpen} onOpenChange={(open) => { if (!open) setSqlDetailOpen(false) }}>
        <DialogContent className="h-[min(720px,calc(100vh-4rem))] max-w-4xl !p-0 overflow-hidden">
          <DialogHeader className="border-b border-border/60 px-4 py-3 pr-12">
            <DialogTitle className="text-sm">
              {t('stage.queryEditor.result.cellDetailTitle')}
            </DialogTitle>
            <DialogDescription className="text-xs">
              {t('stage.queryEditor.result.cellDetailDescription', {
                row: '-',
                column: 'SQL',
                length: result.statementText?.length ?? 0,
              })}
            </DialogDescription>
          </DialogHeader>
          <div className="min-h-0 flex-1 p-4">
            <div
              data-component="markdown-code"
              className="flex h-full min-h-0 flex-col"
              style={{ margin: 0 }}
            >
              <div data-slot="markdown-code-bar">
                <span data-slot="markdown-code-language">SQL</span>
                <div data-slot="markdown-code-actions">
                  <button
                    type="button"
                    aria-pressed={sqlFormatted}
                    onClick={() => setSqlFormatted((current) => !current)}
                  >
                    {t('stage.queryEditor.result.formatContent')}
                  </button>
                  <button
                    type="button"
                    onClick={() => void copyToClipboard(sqlFormatted ? formatSql(result.statementText ?? '', connectionKind) : (result.statementText ?? ''))}
                  >
                    {t('stage.queryEditor.result.copyCell')}
                  </button>
                </div>
              </div>
              <pre
                className="min-h-0 flex-1"
                style={{ overflow: 'auto' }}
              >
                <code
                  style={{
                    whiteSpace: 'pre-wrap',
                    overflowWrap: 'break-word',
                    wordBreak: 'break-word',
                  }}
                >
                  {sqlFormatted ? formatSql(result.statementText ?? '', connectionKind) : result.statementText}
                </code>
              </pre>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('stage.queryEditor.undo.confirmTitle')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('stage.queryEditor.undo.confirmDescription')}
            </AlertDialogDescription>
            <div className="space-y-2 mt-2">
              <pre className="max-h-48 overflow-auto rounded-md bg-muted p-3 text-xs font-mono">
                {inverseSql}
              </pre>
              <p className="text-sm text-muted-foreground">
                {t('stage.queryEditor.undo.affectedTable')}：<code className="font-mono">{tableName}</code>
                {' '}({t('stage.queryEditor.undo.rows', { count: result.affectedRows ?? 0 })})
              </p>
            </div>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={undoing}>{t('stage.queryEditor.undo.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleConfirmUndo}
              disabled={undoing}
              className="bg-[var(--dt-accent-warn)] text-white hover:bg-[var(--dt-accent-warn)]/90"
            >
              {undoing ? t('stage.queryEditor.undo.undoing') : t('stage.queryEditor.undo.confirm')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
