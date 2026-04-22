import type { SqlExecuteResultItem } from '@/services/api/sql'
import { useI18n } from '@/i18n/use-i18n'
import { Markdown } from '@/features/chat/components/markdown/markdown'

type SqlErrorResultPanelProps = {
  result: SqlExecuteResultItem
}

export function SqlErrorResultPanel({ result }: SqlErrorResultPanelProps) {
  const { t } = useI18n()
  const markdown = result.errorMessage ?? t('stage.result.error.default')

  return (
    <div className="h-full overflow-auto px-4 py-4">
      <div className="rounded-xl border border-destructive/15 bg-destructive/[0.03] p-4">
        <Markdown
          text={markdown}
          cacheKey={`sql-error:${result.resultId}:${markdown}`}
          className="text-sm text-foreground"
        />
      </div>
    </div>
  )
}
