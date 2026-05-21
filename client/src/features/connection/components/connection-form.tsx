import { useI18n } from '@/i18n/use-i18n'

export function ConnectionForm() {
  const { t } = useI18n()
  return (
    <div className="flex flex-col gap-3 p-4">
      <p className="text-sm text-muted-foreground">
        {t('dataSources.create')}
      </p>
    </div>
  )
}
