import { Badge } from '@/components/ui/badge'

type SqlEditorBreadcrumbProps = {
  connectionLabel: string | null
  database: string | null
  schema: string | null
  line: number
  kind: string | null
}

export function SqlEditorBreadcrumb({
  connectionLabel,
  database,
  schema,
  line,
  kind,
}: SqlEditorBreadcrumbProps) {
  return (
    <div
      data-testid="sql-editor-breadcrumb"
      className="flex flex-wrap items-center gap-2 border-b border-border/50 bg-muted/20 px-3 py-2 text-xs"
    >
      <Badge variant="secondary" className="max-w-full truncate">
        {connectionLabel ?? 'No connection'}
      </Badge>
      {database ? <Badge variant="outline">{database}</Badge> : null}
      {schema ? <Badge variant="outline">{schema}</Badge> : null}
      <Badge variant="outline">Ln {line}</Badge>
      <Badge variant="secondary">{kind ?? 'UNKNOWN'}</Badge>
    </div>
  )
}
