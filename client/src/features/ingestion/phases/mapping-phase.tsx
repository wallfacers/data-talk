import { useI18n } from '@/i18n/use-i18n'
import { useIngestionJobsStore, type MappingColumnEdit } from '../stores/use-ingestion-jobs-store'
import { usePayloadPreviewQuery } from '../hooks/use-payload-preview-query'
import { MappingEditor } from '../components/mapping-editor'
import { PayloadPreviewTable } from '../components/payload-preview-table'
import { SourceSummaryCard } from '../components/source-summary-card'
import { DdlPreview } from '../components/ddl-preview'
import { Loader2 } from 'lucide-react'
import type { IngestionJobView } from '../api/ingestion-api'

interface MappingPhaseProps {
  job: IngestionJobView
  onConfirm: () => void
  onCancel: () => void
  confirming?: boolean
}

function buildSuggestedDdl(columns: MappingColumnEdit[], tableName: string): string {
  const active = columns.filter((c) => !c.skip)
  if (active.length === 0) return ''
  const lines = active.map((col) => {
    const nullable = col.nullable ? '' : ' NOT NULL'
    return `  "${col.targetName}" ${col.type}${nullable}`
  })
  return `CREATE TABLE "${tableName}" (\n${lines.join(',\n')}\n);`
}

export function MappingPhase({ job, onConfirm, onCancel, confirming }: MappingPhaseProps) {
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
      <div>
        <span className="text-xs font-medium text-muted-foreground mb-1.5 block">{t('ingestion.source.summary')}</span>
        <SourceSummaryCard
          sourceUrl={job.sourceUrl}
          payloadFormat={job.payloadFormat}
          bytesFetched={job.bytesFetched}
          rowCount={job.rowCount}
        />
      </div>

      {columns.length > 0 && (
        <div>
          <span className="text-xs font-medium text-muted-foreground mb-1.5 block">{t('ingestion.mapping.column.target')}</span>
          <MappingEditor columns={columns} ddl={ddl} onChange={handleMappingChange} />
        </div>
      )}

      {ddl && (
        <div>
          <span className="text-xs font-medium text-muted-foreground mb-1.5 block">{t('ingestion.ddl.preview')}</span>
          <DdlPreview ddl={ddl} />
        </div>
      )}

      {preview && (
        <div>
          <span className="text-xs font-medium text-muted-foreground mb-1.5 block">{t('ingestion.payload.preview')}</span>
          <PayloadPreviewTable columns={preview.columns} rows={preview.rows} totalRows={preview.totalRows} />
        </div>
      )}

      <div className="flex items-center gap-2 pt-2">
        <button
          data-testid="ingestion-confirm-btn"
          onClick={onConfirm}
          disabled={columns.length === 0 || confirming}
          className="flex items-center gap-1.5 px-4 py-1.5 rounded-md text-xs font-medium bg-primary text-primary-foreground hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {confirming && <Loader2 className="size-3 animate-spin" />}
          {confirming ? t('ingestion.confirm.button.loading') : t('ingestion.confirm.button')}
        </button>
        <button
          data-testid="ingestion-cancel-btn"
          onClick={onCancel}
          className="px-4 py-1.5 rounded-md text-xs text-muted-foreground border border-border hover:bg-muted/50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          {t('ingestion.cancel.button')}
        </button>
      </div>
    </div>
  )
}
