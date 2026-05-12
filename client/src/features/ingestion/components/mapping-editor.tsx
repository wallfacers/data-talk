import { cn } from '@/lib/utils'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

export interface MappingColumn {
  sourcePath: string
  targetName: string
  type: string
  skip: boolean
  sampleValues: string[]
  nullable: boolean
}

const SQL_TYPES = [
  'BOOLEAN', 'INTEGER', 'BIGINT', 'DECIMAL(18,4)',
  'DATE', 'TIMESTAMP',
  'VARCHAR(64)', 'VARCHAR(256)', 'VARCHAR(500)', 'TEXT',
] as const

interface MappingEditorProps {
  columns: MappingColumn[]
  ddl: string | null
  onChange: (columns: MappingColumn[]) => void
  className?: string
}

export function MappingEditor({ columns, ddl: _ddl, onChange, className }: MappingEditorProps) {
  function updateColumn(index: number, patch: Partial<MappingColumn>) {
    const next = columns.map((col, i) => (i === index ? { ...col, ...patch } : col))
    onChange(next)
  }

  return (
    <div className={cn('flex flex-col gap-3', className)}>
      <div className="border border-border-default rounded-md overflow-auto max-h-[400px]">
        <table className="w-full text-ui-sm">
          <thead>
            <tr className="bg-bg-subtle">
              <th className="px-2 py-1.5 text-left text-ui-xs font-medium text-text-muted w-8">Skip</th>
              <th className="px-2 py-1.5 text-left text-ui-xs font-medium text-text-muted">Source</th>
              <th className="px-2 py-1.5 text-left text-ui-xs font-medium text-text-muted">Target</th>
              <th className="px-2 py-1.5 text-left text-ui-xs font-medium text-text-muted w-40">SQL Type</th>
              <th className="px-2 py-1.5 text-left text-ui-xs font-medium text-text-muted">Samples</th>
              <th className="px-2 py-1.5 text-left text-ui-xs font-medium text-text-muted w-16">Null</th>
            </tr>
          </thead>
          <tbody>
            {columns.map((col, i) => (
              <tr
                key={col.sourcePath}
                data-testid={`mapping-row-${col.sourcePath}`}
                className={cn(
                  'border-t border-border-subtle hover:bg-interaction-hover transition-colors',
                  col.skip && 'bg-bg-subtle text-text-muted line-through',
                )}
              >
                <td className="px-2 py-1">
                  <Checkbox
                    checked={col.skip}
                    onCheckedChange={(checked) => updateColumn(i, { skip: !!checked })}
                    className="h-3.5 w-3.5"
                  />
                </td>
                <td className="px-2 py-1 text-text-muted font-mono text-ui-xs truncate max-w-[180px]" title={col.sourcePath}>
                  {col.sourcePath}
                </td>
                <td className="px-2 py-1">
                  <Input
                    value={col.targetName}
                    onChange={(e) => updateColumn(i, { targetName: e.target.value })}
                    className="h-6 text-ui-xs px-1.5 border-border-default focus-visible:ring-interaction-focusRing"
                    disabled={col.skip}
                  />
                </td>
                <td className="px-2 py-1">
                  <Select
                    value={mapInferredTypeToSelect(col.type)}
                    onValueChange={(v) => updateColumn(i, { type: v ?? '' })}
                    disabled={col.skip}
                  >
                    <SelectTrigger className="h-6 text-ui-xs px-1.5 w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {SQL_TYPES.map((t) => (
                        <SelectItem key={t} value={t} className="text-ui-xs">
                          {t}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </td>
                <td className="px-2 py-1 text-text-muted text-ui-xs truncate max-w-[120px]" title={col.sampleValues.join(', ')}>
                  {col.sampleValues.join(', ')}
                </td>
                <td className="px-2 py-1 text-ui-xs text-text-muted">{col.nullable ? '✓' : '✗'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function mapInferredTypeToSelect(type: string): string {
  const mapping: Record<string, string> = {
    BOOLEAN: 'BOOLEAN',
    INTEGER_32: 'INTEGER',
    INTEGER_64: 'BIGINT',
    DECIMAL: 'DECIMAL(18,4)',
    DATE: 'DATE',
    TIMESTAMP: 'TIMESTAMP',
    STRING_64: 'VARCHAR(64)',
    STRING_256: 'VARCHAR(256)',
    STRING_500: 'VARCHAR(500)',
    STRING_LONG: 'TEXT',
    JSON: 'TEXT',
  }
  return mapping[type] ?? type
}
