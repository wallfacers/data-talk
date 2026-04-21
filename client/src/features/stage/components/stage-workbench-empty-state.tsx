import { DatabaseIcon, SparklesIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
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
  description: string
  primaryActionLabel: string
  onPrimaryAction?: () => void
}

export function StageWorkbenchEmptyState({
  title,
  description,
  primaryActionLabel,
  onPrimaryAction,
}: Props) {
  const { t } = useI18n()

  return (
    <div data-testid="stage-empty-workbench" className="flex h-full min-h-0 flex-col gap-4 overflow-auto p-4">
      <Card className="border-border/60 bg-background/95 shadow-sm">
        <CardHeader className="gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <DatabaseIcon className="size-5" />
          </div>
          <CardTitle className="text-lg">{title}</CardTitle>
          <CardDescription className="max-w-2xl">{description}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap items-center gap-3">
          <Button type="button" onClick={onPrimaryAction}>
            {primaryActionLabel}
          </Button>
          <div className="inline-flex items-center gap-2 rounded-md border border-dashed border-border/60 px-3 py-2 text-xs text-muted-foreground">
            <SparklesIcon className="size-3.5" />
            {t('stage.empty.helper')}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
