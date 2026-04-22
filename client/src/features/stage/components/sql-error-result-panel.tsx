import type { SqlExecuteResultItem } from '@/services/api/sql'
import { useI18n } from '@/i18n/use-i18n'

type SqlErrorResultPanelProps = {
  result: SqlExecuteResultItem
}

export function SqlErrorResultPanel({ result }: SqlErrorResultPanelProps) {
  const { t } = useI18n()

  return (
    <div className="flex h-full items-center justify-center px-6 py-6 text-center">
      <p className="text-sm font-medium text-destructive">
        {result.errorMessage ?? t('stage.result.error.default')}
      </p>
    </div>
  )
}
