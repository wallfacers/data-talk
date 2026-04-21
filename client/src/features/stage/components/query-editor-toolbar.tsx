import { SqlEditorHeader } from './sql-editor-header'

type QueryEditorToolbarProps = {
  entryLabel: string
  connectionLabel: string
  detailLabel: string | null
  contextNotice: string | null
  runLabel: string
  isRunning: boolean
  showRunButton: boolean
  onRun: () => void
}

export function QueryEditorToolbar({
  entryLabel,
  connectionLabel,
  detailLabel,
  contextNotice,
  runLabel,
  isRunning,
  showRunButton,
  onRun,
}: QueryEditorToolbarProps) {
  return (
    <SqlEditorHeader
      entryLabel={entryLabel}
      connectionLabel={connectionLabel}
      detailLabel={detailLabel}
      contextNotice={contextNotice}
      runLabel={runLabel}
      canRun={showRunButton}
      isRunning={isRunning}
      onRun={onRun}
    />
  )
}
