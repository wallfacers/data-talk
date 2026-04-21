import { SqlResultPanel } from './sql-result-panel'

type QueryEditorDisplayResult = {
  columns: string[]
  rows: unknown[][]
  rowCount: number
  executionMs: number
  truncated: boolean
}

type QueryEditorResultPanelProps = {
  status: 'idle' | 'running' | 'success' | 'risk_blocked' | 'error'
  result: QueryEditorDisplayResult | null
  title: string
  summary: string | null
  idleLabel: string
  runningLabel: string
  errorLabel: string
  riskTitle: string
  riskReason: string | null
  cancelLabel: string
  sendToAiLabel: string
  onCancel: () => void
  onSendToAi: () => void
}

export function QueryEditorResultPanel({
  status,
  result,
  title: _title,
  summary: _summary,
  idleLabel: _idleLabel,
  runningLabel: _runningLabel,
  errorLabel: _errorLabel,
  riskTitle: _riskTitle,
  riskReason,
  cancelLabel: _cancelLabel,
  sendToAiLabel: _sendToAiLabel,
  onCancel: _onCancel,
  onSendToAi: _onSendToAi,
}: QueryEditorResultPanelProps) {
  return (
    <SqlResultPanel
      executeStatus={status}
      activeResult={result
        ? {
            resultId: 'legacy',
            kind: 'result_set',
            title: 'Result',
            statementIndex: 0,
            statementText: '',
            columns: result.columns,
            rows: result.rows,
            rowCount: result.rowCount,
            executionMs: result.executionMs,
            truncated: result.truncated,
          }
        : null}
      risk={riskReason ? { riskLevel: 'HIGH', riskReason } : null}
      errorMessage={null}
    />
  )
}
