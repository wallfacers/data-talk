import type { SqlExecuteResultItem } from '@/services/api/sql'
import { useI18n } from '@/i18n/use-i18n'
import type { SqlWorkbenchExecuteStatus } from '../stores/sql-workbench-store'
import { SqlResultTable } from './sql-result-table'
import type { ResultScrollPosition } from './sql-result-table'
import { SqlDmlSummaryPanel } from './sql-dml-summary-panel'
import { SqlErrorResultPanel } from './sql-error-result-panel'

type SqlResultPanelProps = {
  executeStatus: SqlWorkbenchExecuteStatus
  activeResult: SqlExecuteResultItem | null
  errorMessage: string | null
  activeScrollPosition?: ResultScrollPosition
  onActiveScrollPositionChange?: (position: ResultScrollPosition) => void
}

export function SqlResultPanel({
  executeStatus,
  activeResult,
  errorMessage,
  activeScrollPosition,
  onActiveScrollPositionChange,
}: SqlResultPanelProps) {
  const { t } = useI18n()

  if (executeStatus === 'running') {
    return (
      <div className="flex h-full items-center justify-center px-4 text-xs text-muted-foreground">
        {t('stage.queryEditor.running')}
      </div>
    )
  }

  if (!activeResult) {
    if (executeStatus === 'error') {
      return (
        <SqlErrorResultPanel
          result={{
            resultId: 'sql-error-fallback',
            kind: 'error',
            title: t('stage.status.error'),
            statementIndex: 0,
            statementText: '',
            columns: [],
            rows: [],
            rowCount: 0,
            executionMs: 0,
            truncated: false,
            errorMessage: errorMessage ?? t('stage.queryEditor.runFailed'),
          }}
        />
      )
    }

    return (
      <div className="flex h-full items-center justify-center px-4 text-xs text-muted-foreground">
        {t('stage.queryEditor.runToSeeResults')}
      </div>
    )
  }

  if (activeResult.kind === 'dml_summary') {
    return <SqlDmlSummaryPanel result={activeResult} />
  }

  if (activeResult.kind === 'error') {
    return <SqlErrorResultPanel result={activeResult} />
  }

  return (
    <SqlResultTable
      result={activeResult}
      scrollPosition={activeScrollPosition}
      onScrollPositionChange={onActiveScrollPositionChange}
    />
  )
}
