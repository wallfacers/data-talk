import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { useI18n } from '@/i18n/use-i18n'

type Props = {
  title: string
  kind: 'er' | 'report' | 'dashboard' | 'unsupported'
  description: string
  details?: string
}

export function StagePlaceholderTab({ title, kind, description, details }: Props) {
  const { t } = useI18n()
  const kindLabelKey = {
    er: 'stage.kind.er',
    report: 'stage.kind.report',
    dashboard: 'stage.kind.dashboard',
    unsupported: 'stage.kind.unsupported',
  }[kind] as 'stage.kind.er' | 'stage.kind.report' | 'stage.kind.dashboard' | 'stage.kind.unsupported'

  return (
    <div data-testid="stage-placeholder-tab" className="flex h-full min-h-0 flex-col gap-4 overflow-auto p-4">
      <Card className="border-border/60 bg-background/95 shadow-sm">
        <CardHeader className="gap-3">
          <div className="flex items-center gap-3">
            <Badge variant="secondary">{t(kindLabelKey)}</Badge>
            <CardTitle className="text-lg">{title}</CardTitle>
          </div>
          <CardDescription className="max-w-2xl">{description}</CardDescription>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          {t('stage.placeholder.helper')}
          {details ? <div className="mt-2 text-xs text-muted-foreground">{details}</div> : null}
        </CardContent>
      </Card>
    </div>
  )
}
