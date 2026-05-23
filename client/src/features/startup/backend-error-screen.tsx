import { useState } from 'react'
import { AlertTriangleIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { retryBackendStartup } from './use-backend-status'

export function BackendErrorScreen({
  message,
  logPath,
}: {
  message: string
  logPath: string
}) {
  const { t } = useI18n()
  const [copied, setCopied] = useState(false)

  const onCopyLog = async () => {
    try {
      await navigator.clipboard.writeText(logPath)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      /* clipboard unavailable — the path is shown below for manual copy */
    }
  }

  return (
    <div className="flex h-screen w-screen flex-col items-center justify-center gap-4 bg-background px-6 text-center text-foreground">
      <div className="flex size-12 items-center justify-center rounded-xl bg-destructive/10 text-destructive">
        <AlertTriangleIcon className="size-6" />
      </div>
      <h1 className="text-lg font-semibold tracking-tight">{t('startup.errorTitle')}</h1>
      <p className="max-w-md text-sm text-muted-foreground">{message}</p>
      {logPath ? (
        <code className="max-w-md break-all rounded bg-muted px-2 py-1 text-xs text-muted-foreground">
          {logPath}
        </code>
      ) : null}
      <div className="mt-1 flex gap-2">
        <Button onClick={() => void retryBackendStartup()}>{t('common.retry')}</Button>
        {logPath ? (
          <Button variant="outline" onClick={() => void onCopyLog()}>
            {copied ? t('startup.logPathCopied') : t('startup.viewLog')}
          </Button>
        ) : null}
      </div>
    </div>
  )
}
