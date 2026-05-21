import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

type QueryEditorInspectorProps = {
  title: string
  description: string
  connectionLabel: string
  databaseLabel: string
  schemaLabel: string
  lastRunLabel: string
  connectionFieldLabel: string
  databaseFieldLabel: string
  schemaFieldLabel: string
  lastRunFieldLabel: string
}

function InspectorRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="space-y-1">
      <div className="text-muted-foreground">{label}</div>
      <div className="break-words text-foreground">{value}</div>
    </div>
  )
}

export function QueryEditorInspector({
  title,
  description,
  connectionLabel,
  databaseLabel,
  schemaLabel,
  lastRunLabel,
  connectionFieldLabel,
  databaseFieldLabel,
  schemaFieldLabel,
  lastRunFieldLabel,
}: QueryEditorInspectorProps) {
  return (
    <Card className="h-full border-border/60 bg-muted/10">
      <CardHeader className="gap-2">
        <CardTitle className="text-sm">{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 text-xs">
        <InspectorRow label={connectionFieldLabel} value={connectionLabel} />
        <InspectorRow label={databaseFieldLabel} value={databaseLabel} />
        <InspectorRow label={schemaFieldLabel} value={schemaLabel} />
        <InspectorRow label={lastRunFieldLabel} value={lastRunLabel} />
      </CardContent>
    </Card>
  )
}
