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
import { useI18n } from '@/i18n/use-i18n'
import { RotateCcw } from 'lucide-react'
import { useState, useCallback } from 'react'
import { useSqlWorkbenchStore } from '@/features/stage/stores/sql-workbench-store'
import { toast } from 'sonner'

type SqlDmlSummaryPanelProps = {
  result: SqlExecuteResultItem
  tabId: string
}

export function SqlDmlSummaryPanel({ result, tabId }: SqlDmlSummaryPanelProps) {
  const { t } = useI18n()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [inverseSql, setInverseSql] = useState<string | null>(null)
  const [undoing, setUndoing] = useState(false)
  const setUndoConfirming = useSqlWorkbenchStore((s) => s.setUndoConfirming)
  const setUndoResult = useSqlWorkbenchStore((s) => s.setUndoResult)
  const undoState = useSqlWorkbenchStore(
    (s) => s.tabsById[tabId]?.undoStates?.[result.resultId],
  )

  const isUndone = undoState?.status === 'undone'
  const undoError = undoState?.status === 'error' ? undoState.error : null

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
    <div className="h-full overflow-auto">
      <Table className="text-xs">
        <TableHeader className="sticky top-0 bg-muted/40">
          <TableRow className="hover:bg-transparent">
            <TableHead className="h-8 w-14 border-b border-border/50 px-3 text-center">
              {t('stage.queryEditor.result.rowNumber')}
            </TableHead>
            <TableHead className="h-8 border-b border-border/50 px-3">
              {t('stage.queryEditor.result.action')}
            </TableHead>
            <TableHead className="h-8 border-b border-border/50 px-3">
              {t('stage.queryEditor.result.affectedRows')}
            </TableHead>
            <TableHead className="h-8 border-b border-border/50 px-3">
              {t('stage.queryEditor.result.duration')}
            </TableHead>
            <TableHead className="h-8 border-b border-border/50 px-3">
              {t('stage.queryEditor.result.sql')}
            </TableHead>
            <TableHead className="h-8 w-20 border-b border-border/50 px-3" />
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow className="border-b border-border/30">
            <TableCell className="px-3 py-1.5 text-center text-muted-foreground">1</TableCell>
            <TableCell className="px-3 py-1.5">
              {isUndone ? (
                <span className="text-muted-foreground">{result.title} ({t('stage.queryEditor.undo.reverted')})</span>
              ) : (
                result.title
              )}
            </TableCell>
            <TableCell className="px-3 py-1.5">{result.affectedRows ?? 0}</TableCell>
            <TableCell className="px-3 py-1.5">{result.executionMs}ms</TableCell>
            <TableCell className="max-w-[640px] truncate px-3 py-1.5">{result.statementText}</TableCell>
            <TableCell className="px-3 py-1.5">
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
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>

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
