import { useState } from 'react'
import { FileText, FileSpreadsheet, FileJson, ChevronDownIcon } from 'lucide-react'
import type { FileUploadPart } from '@/services/channel/types'
import { cn } from '@/lib/utils'

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function fileTypeIcon(filename: string) {
  const ext = filename.split('.').pop()?.toLowerCase()
  switch (ext) {
    case 'csv': case 'xlsx': case 'xls':
      return <FileSpreadsheet className="h-4 w-4 shrink-0" />
    case 'json': case 'jsonl':
      return <FileJson className="h-4 w-4 shrink-0" />
    default:
      return <FileText className="h-4 w-4 shrink-0" />
  }
}

function AnalysisBadge({ label, value }: { label: string; value: unknown }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-md bg-primary-foreground/15 px-1.5 py-0.5 text-[10px] tabular-nums">
      <span className="font-medium">{label}</span>
      <span>{String(value)}</span>
    </span>
  )
}

function AnalysisMetrics({ analysis }: { analysis: Record<string, unknown> }) {
  const metrics: Array<{ label: string; value: unknown }> = []

  // CSV/Excel metrics
  if (typeof analysis.estimatedRows === 'number') {
    metrics.push({ label: 'rows', value: analysis.estimatedRows.toLocaleString() })
  }
  const headers = analysis.headers
  if (Array.isArray(headers) && headers.length > 0) {
    metrics.push({ label: 'cols', value: headers.length })
  }

  // SQL metrics
  if (typeof analysis.statementCount === 'number') {
    metrics.push({ label: 'statements', value: analysis.statementCount })
  }
  if (typeof analysis.riskLevel === 'string') {
    metrics.push({ label: 'risk', value: analysis.riskLevel })
  }

  // JSON metrics
  if (typeof analysis.arrayLength === 'number') {
    metrics.push({ label: 'items', value: analysis.arrayLength.toLocaleString() })
  }
  if (typeof analysis.nestingDepth === 'number') {
    metrics.push({ label: 'depth', value: analysis.nestingDepth })
  }
  if (typeof analysis.structure === 'string') {
    metrics.push({ label: 'type', value: analysis.structure })
  }

  // Text metrics
  if (typeof analysis.lineCount === 'number') {
    metrics.push({ label: 'lines', value: analysis.lineCount.toLocaleString() })
  }

  // Excel sheets
  const sheets = analysis.sheets
  if (Array.isArray(sheets)) {
    metrics.push({ label: 'sheets', value: sheets.length })
  }

  if (metrics.length === 0) return null

  return (
    <div className="flex flex-wrap gap-1">
      {metrics.map((m) => (
        <AnalysisBadge key={m.label} label={m.label} value={m.value} />
      ))}
    </div>
  )
}

function PreviewSection({ preview }: { preview: unknown }) {
  const [open, setOpen] = useState(false)

  if (!Array.isArray(preview) || preview.length === 0) return null

  const lines = preview.slice(0, 5)

  return (
    <div className="mt-1">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1 text-[10px] text-primary-foreground/70 hover:text-primary-foreground transition-colors"
      >
        <ChevronDownIcon className={cn('h-3 w-3 transition-transform', open && 'rotate-180')} />
        Preview
      </button>
      {open && (
        <div className="mt-1 max-h-32 overflow-auto rounded-md bg-primary-foreground/10 p-2 text-[10px] font-mono leading-relaxed">
          {lines.map((line, i) => (
            <div key={i} className="whitespace-pre-wrap break-all">{String(line)}</div>
          ))}
          {preview.length > 5 && (
            <div className="text-primary-foreground/50">... and {preview.length - 5} more</div>
          )}
        </div>
      )}
    </div>
  )
}

export function FileUploadCard({ part }: { part: FileUploadPart }) {
  const { filename, sizeBytes, analysis } = part
  const analysisType = typeof analysis.type === 'string' ? analysis.type : 'FILE'

  return (
    <div className="flex flex-col gap-1.5 rounded-md bg-primary-foreground/10 px-2.5 py-2">
      {/* Header row: icon + filename + size + type badge */}
      <div className="flex items-center gap-2">
        <span className="shrink-0 text-primary-foreground/75">
          {fileTypeIcon(filename)}
        </span>
        <span className="truncate text-xs font-medium text-primary-foreground">{filename}</span>
        <span className="shrink-0 text-[10px] text-primary-foreground/60">{formatSize(sizeBytes)}</span>
        <span className="ml-auto shrink-0 rounded bg-primary-foreground/20 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-primary-foreground/80">
          {analysisType}
        </span>
      </div>

      {/* Analysis metrics */}
      <AnalysisMetrics analysis={analysis} />

      {/* Preview */}
      {Array.isArray(analysis.preview) && <PreviewSection preview={analysis.preview} />}
    </div>
  )
}
