import { useState } from 'react'
import { CopyIcon, CheckIcon, TerminalIcon } from 'lucide-react'
import type { MessageInfo, Part, TextPart } from '@/services/channel/types'
import { useChannel } from '@/services/channel/use-channel'
import { cn, copyToClipboard } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'

function HighlightedText(props: { text: string }) {
  return <>{props.text}</>
}

export function UserBubble(props: { info: MessageInfo; parts: Part[] }) {
  const { t, language } = useI18n()
  const { info, parts } = props
  const textPart = parts.find((p) => p.type === 'text') as TextPart | undefined
  const text = textPart?.text ?? ''
  const displayKind = (textPart?.metadata as { displayKind?: string } | undefined)?.displayKind
  const isBangQueryUser = displayKind === 'bang_query_user'
  const [copied, setCopied] = useState(false)
  const channel = useChannel()

  const pending = !!info.__pending
  const failed = !!info.__failed
  const retrying = !!info.__retrying

  const handleCopy = async () => {
    const success = await copyToClipboard(text)
    if (success) {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  const handleRetry = async () => {
    if (!retrying && channel.retryPendingUser) {
      await channel.retryPendingUser(info.id, [{ type: 'text', text }])
    }
  }

  const handleRemove = () => {
    channel.removePendingUser?.(info.id)
  }

  return (
    <div className={cn('flex flex-col items-end gap-1 my-2')}>
      <div className={cn(
        'relative max-w-[85%] rounded-lg px-3 py-2 text-sm',
        'bg-primary text-primary-foreground',
        isBangQueryUser && 'pr-7',
        pending && !failed && 'opacity-85',
        failed && 'border-2 border-red-500',
      )}>
        {isBangQueryUser && (
          <span
            aria-label={t('bangQuery.userMarker')}
            className="pointer-events-none absolute right-1.5 top-1.5 inline-flex text-white/55"
            role="img"
          >
            <TerminalIcon className="size-3" aria-hidden="true" />
          </span>
        )}
        <HighlightedText text={text} />
        {retrying && <span className="ml-2 inline-block animate-spin">⟳</span>}
      </div>
      {failed && (
        <div className="flex gap-2 text-xs">
          <span className="text-red-500">⚠ {t('chat.sendFailed', { reason: info.__failReason ?? '' })}</span>
          <button onClick={handleRetry} className="text-primary hover:underline">{t('common.retry')}</button>
          <button onClick={handleRemove} className="text-muted-foreground hover:underline">{t('common.delete')}</button>
        </div>
      )}
      {!pending && !failed && (
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <button onClick={handleCopy} className="flex items-center hover:text-foreground" aria-label={t('common.copy')}>
            {copied ? <CheckIcon className="size-3.5" /> : <CopyIcon className="size-3.5" />}
          </button>
          {info.time.created && <span>· {new Date(info.time.created).toLocaleTimeString(language)}</span>}
        </div>
      )}
    </div>
  )
}
