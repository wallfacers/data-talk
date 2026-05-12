import { useI18n } from '@/i18n/use-i18n'
import { useIngestionJobsStore, type MappingColumnEdit } from '../stores/use-ingestion-jobs-store'
import { usePayloadPreviewQuery } from '../hooks/use-payload-preview-query'
import { MappingEditor } from '../components/mapping-editor'
import { PayloadPreviewTable } from '../components/payload-preview-table'
import { SourceSummaryCard } from '../components/source-summary-card'
import { DdlPreview } from '../components/ddl-preview'
import type { IngestionJobView } from '../api/ingestion-api'

interface MappingPhaseProps {
  job: IngestionJobView
  onConfirm: () => void
  onCancel: () => void
}

function buildSuggestedDdl(columns: MappingColumnEdit[], tableName: string): string {
  const active = columns.filter((c) => !c.skip)
  if (active.length === 0) return ''
  const lines = active.map((col) => `  "${col.targetName}" ${col.type}`)
  return `CREATE TABLE "${tableName}" (\n${lines.join(',\n')}\n);`
}

export function MappingPhase({ job, onConfirm, onCancel }: MappingPhaseProps) {
  const { t } = useI18n()
  const { editingMapping, setMappingEdit } = useIngestionJobsStore()
  const { data: preview } = usePayloadPreviewQuery(job.payloadArtifactId ? job.id : null)

  const existing = editingMapping.get(job.id)
  const columns: MappingColumnEdit[] = existing?.columns ?? []
  const targetTable = existing?.targetTable ?? job.targetTable ?? 'ingested_data'
  const targetSchema = existing?.targetSchema ?? job.targetSchema

  function handleMappingChange(cols: MappingColumnEdit[]) {
    setMappingEdit(job.id, { columns: cols, targetTable, targetSchema })
  }

  const ddl = buildSuggestedDdl(columns, targetTable)

  return (
    <div className="flex flex-col gap-4 p-4">
      <SourceSummaryCard
        sourceUrl={job.sourceUrl}
        payloadFormat={job.payloadFormat}
        bytesFetched={job.bytesFetched}
        rowCount={job.rowCount}
      />

      {columns.length > 0 && (
        <MappingEditor columns={columns} ddl={ddl} onChange={handleMappingChange} />
      )}

      {ddl && <DdlPreview ddl={ddl} />}

      {preview && (
        <PayloadPreviewTable columns={preview.columns} rows={preview.rows} totalRows={preview.totalRows} />
      )}

      <div className="flex items-center gap-2 pt-2">
        <button
          onClick={onConfirm}
          disabled={columns.length === 0}
          className="px-4 py-1.5 rounded-md text-ui-sm font-medium bg-accent-primary text-text-inverse hover:bg-accent-primaryHover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-interaction-focusRing disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {t('ingestion.confirm.button')}
        </button>
        <button
          onClick={onCancel}
          className="px-4 py-1.5 rounded-md text-ui-sm text-text-muted border border-border-default hover:bg-interaction-hover hover:text-text-base focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-interaction-focusRing"
        >
          {t('ingestion.cancel.button')}
        </button>
      </div>
    </div>
  )
}
