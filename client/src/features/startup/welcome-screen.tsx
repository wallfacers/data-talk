import { DatabaseIcon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'

export function WelcomeScreen() {
  const { t } = useI18n()
  return (
    <div className="flex h-screen w-screen flex-col items-center justify-center gap-5 bg-background text-foreground">
      <div className="flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
        <DatabaseIcon className="size-6" />
      </div>
      <h1 className="text-xl font-semibold tracking-tight">DataTalk</h1>
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <span
          aria-hidden
          className="size-4 animate-spin rounded-full border-2 border-muted-foreground/30 border-t-primary motion-reduce:animate-none"
        />
        <span>{t('startup.starting')}</span>
      </div>
    </div>
  )
}
