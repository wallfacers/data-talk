import { SparklesIcon } from 'lucide-react'
import type { SqlExecuteResultItem } from '@/services/api/sql'
import { useI18n } from '@/i18n/use-i18n'
import { Markdown } from '@/features/chat/components/markdown/markdown'
import { useAskAIAboutError } from '@/features/chat/error-to-ai/use-ask-ai-about-error'
import type { ErrorContext } from '@/features/chat/error-to-ai/error-to-ai-context'
import { cn } from '@/lib/utils'

type SqlErrorResultPanelProps = {
  result: SqlExecuteResultItem
  connectionName?: string | null
  connectionKind?: string | null
  database?: string | null
  schema?: string | null
  connectionId?: string | null
}

export function SqlErrorResultPanel({
  result,
  connectionName,
  connectionKind,
  database,
  schema,
  connectionId,
}: SqlErrorResultPanelProps) {
  const { t } = useI18n()
  const markdown = result.errorMessage ?? t('stage.result.error.default')

  const errorContext: ErrorContext = {
    title: t('stage.result.error.askAiTitle'),
    connectionName,
    connectionKind,
    database,
    schema,
    statementText: result.statementText || undefined,
    errorMessage: result.errorMessage ?? undefined,
  }

  const { askAI, isAvailable } = useAskAIAboutError(errorContext)
  const showAskAI = isAvailable && connectionId != null

  return (
    <div className="h-full overflow-auto px-4 py-4">
      <div className="rounded-xl border border-destructive/15 bg-destructive/[0.03] p-4">
        <Markdown
          text={markdown}
          cacheKey={`sql-error:${result.resultId}:${markdown}`}
          className="text-sm text-foreground"
        />
        {showAskAI && (
          <div className="mt-3 flex justify-end border-t border-destructive/10 pt-3">
            <button
              type="button"
              onClick={askAI}
              className={cn(
                'inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium',
                'text-accent-primary hover:bg-accent-primary/10 active:bg-accent-primary/15',
                'transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50',
              )}
            >
              <SparklesIcon className="size-3.5" aria-hidden="true" />
              {t('stage.result.error.askAi')}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
