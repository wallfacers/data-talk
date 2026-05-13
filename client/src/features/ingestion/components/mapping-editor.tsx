import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import { Switch } from '@/components/ui/switch'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

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

const stickyHeaderCellClass = 'sticky top-0 z-20 h-8 border-b border-border/50 bg-muted px-3'

interface MappingEditorProps {
  columns: MappingColumn[]
  ddl: string | null
  onChange: (columns: MappingColumn[]) => void
  className?: string
}

export function MappingEditor({ columns, ddl: _ddl, onChange, className }: MappingEditorProps) {
  const { t } = useI18n()

  function updateColumn(index: number, patch: Partial<MappingColumn>) {
    const next = columns.map((col, i) => (i === index ? { ...col, ...patch } : col))
    onChange(next)
  }

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      {/* Skip chips - horizontal */}
      <div className="flex flex-wrap gap-1.5">
        {columns.map((col, i) => (
          <button
            key={col.sourcePath}
            type="button"
            onClick={() => updateColumn(i, { skip: !col.skip })}
            className={cn(
              'inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs border transition-colors cursor-pointer',
              col.skip
                ? 'bg-muted text-muted-foreground line-through border-border/50'
                : 'bg-background text-foreground border-border hover:bg-muted/50',
            )}
            title={t('ingestion.mapping.column.skip')}
          >
            {col.sourcePath.replace(/^\$\.?/, '')}
          </button>
        ))}
      </div>

      {/* Mapping table */}
      <div className="border border-border rounded-md overflow-hidden">
        <div data-result-scrollbar="header-offset" className="overflow-auto max-h-[280px]">
          <Table scrollContainer={false} className="min-w-max text-xs">
            <TableHeader className="bg-muted">
              <TableRow className="hover:bg-transparent">
                <TableHead className={`${stickyHeaderCellClass} font-medium`}>{t('ingestion.mapping.column.source')}</TableHead>
                <TableHead className={`${stickyHeaderCellClass} font-medium`}>{t('ingestion.mapping.column.target')}</TableHead>
                <TableHead className={`${stickyHeaderCellClass} font-medium w-40`}>{t('ingestion.mapping.column.type')}</TableHead>
                <TableHead className={`${stickyHeaderCellClass} font-medium`}>{t('ingestion.mapping.column.samples')}</TableHead>
                <TableHead className={`${stickyHeaderCellClass} font-medium w-20`}>{t('ingestion.mapping.column.nullable')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {columns.map((col, i) => (
                <TableRow
                  key={col.sourcePath}
                  data-testid={`mapping-row-${col.sourcePath}`}
                  className={cn(
                    'hover:bg-muted/50 transition-colors',
                    col.skip && 'bg-muted/50 text-muted-foreground',
                  )}
                >
                  <TableCell className="px-3 py-1.5 font-mono text-muted-foreground truncate max-w-[180px]" title={col.sourcePath}>
                    {col.sourcePath.replace(/^\$\.?/, '')}
                  </TableCell>
                  <TableCell className="px-3 py-1.5">
                    <Input
                      value={col.targetName}
                      onChange={(e) => updateColumn(i, { targetName: e.target.value })}
                      className="h-6 text-xs px-1.5 border-border focus-visible:ring-ring"
                      disabled={col.skip}
                    />
                  </TableCell>
                  <TableCell className="px-3 py-1.5">
                    <Select
                      value={mapInferredTypeToSelect(col.type)}
                      onValueChange={(v) => updateColumn(i, { type: v ?? '' })}
                      disabled={col.skip}
                    >
                      <SelectTrigger className="!h-6 !py-0 text-xs px-1.5 w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {SQL_TYPES.map((st) => (
                          <SelectItem key={st} value={st} className="text-xs py-1">
                            {st}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </TableCell>
                  <TableCell className="px-3 py-1.5 text-muted-foreground truncate max-w-[120px]" title={col.sampleValues.join(', ')}>
                    {col.sampleValues.join(', ')}
                  </TableCell>
                  <TableCell className="px-3 py-1.5">
                    <Switch
                      size="sm"
                      checked={col.nullable}
                      onCheckedChange={(checked) => updateColumn(i, { nullable: !!checked })}
                      disabled={col.skip}
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
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
