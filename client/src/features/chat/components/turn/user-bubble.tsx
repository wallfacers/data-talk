import { useEffect, useRef, useState } from 'react'
import { CopyIcon, CheckIcon, PlayIcon, TerminalIcon } from 'lucide-react'
import type { MessageInfo, Part, TextPart } from '@/services/channel/types'
import { useChannel } from '@/services/channel/use-channel'
import { cn, copyToClipboard } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { openDirectSqlQueryEditorTab } from '@/features/stage/utils/open-direct-sql-query-editor-tab'
import { normalizeError, showErrorToast } from '@/services/http-error'

function HighlightedText(props: { text: string }) {
  return <>{props.text}</>
}

function extractBangQuerySql(text: string): string | null {
  const trimmed = text.trim()
  if (!trimmed.startsWith('!')) return null
  const sql = trimmed.slice(1).trim()
  if (!sql) return null
  if (!/^(select|with)\b/i.test(sql)) return null
  return sql
}

export function UserBubble(props: { info: MessageInfo; parts: Part[] }) {
  const { t, language } = useI18n()
  const { info, parts } = props
  const textPart = parts.find((p) => p.type === 'text') as TextPart | undefined
  const text = textPart?.text ?? ''
  const displayKind = (textPart?.metadata as { displayKind?: string } | undefined)?.displayKind
  const isBangQueryUser = displayKind === 'bang_query_user'
  const bangQuerySql = isBangQueryUser ? extractBangQuerySql(text) : null
  const [copied, setCopied] = useState(false)
  const channel = useChannel()

  const pending = !!info.__pending
  const failed = !!info.__failed
  const retrying = !!info.__retrying
  const [entryMotionActive, setEntryMotionActive] = useState(() => pending && !failed)
  const hasStartedEntryMotion = useRef(pending && !failed)

  useEffect(() => {
    if (!pending || failed || hasStartedEntryMotion.current) return
    hasStartedEntryMotion.current = true
    setEntryMotionActive(true)
  }, [pending, failed])

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

  const handleRerun = async () => {
    if (!bangQuerySql) return
    const sessionId = info.sessionID?.trim().length ? info.sessionID : null
    const sessionContext = sessionId
      ? useSessionStore.getState().dataContextBySession.get(sessionId) ?? null
      : null
    const connectionId = sessionContext?.connectionId ?? useConnectionStore.getState().activeConnectionId

    try {
      await openDirectSqlQueryEditorTab({
        sessionId,
        connectionId,
        sql: bangQuerySql,
      })
    } catch (error) {
      showErrorToast(normalizeError(error))
    }
  }

  return (
    <div
      data-pending-user-motion={pending && !failed ? 'true' : undefined}
      className="my-2 flex flex-col items-end gap-1"
    >
      <div
        data-pending-user-bubble={entryMotionActive ? 'true' : undefined}
        onAnimationEnd={() => setEntryMotionActive(false)}
        className={cn(
          'relative max-w-[85%] rounded-lg px-3 py-2 text-sm',
          'bg-primary text-primary-foreground',
          pending && !failed && 'opacity-85',
          entryMotionActive && 'motion-safe:animate-in motion-safe:slide-in-from-bottom-5 motion-safe:duration-300 motion-safe:ease-out motion-safe:will-change-transform',
          failed && 'border-2 border-red-500',
        )}
      >
        {isBangQueryUser ? (
          <div className="flex items-center gap-1.5">
            <span
              aria-label={t('bangQuery.userMarker')}
              className="pointer-events-none inline-flex shrink-0 text-primary-foreground/65"
              role="img"
            >
              <TerminalIcon className="size-3" aria-hidden="true" />
            </span>
            <span className="min-w-0 flex-1 whitespace-pre-wrap break-words leading-5">
              <HighlightedText text={text} />
            </span>
            {bangQuerySql ? (
              <Tooltip>
                <TooltipTrigger
                  render={
                    <button
                      type="button"
                      aria-label={t('bangQuery.rerun')}
                      onClick={(event) => {
                        event.stopPropagation()
                        void handleRerun()
                      }}
                      className="inline-flex size-5 shrink-0 items-center justify-center rounded-md text-primary-foreground/75 transition-colors hover:bg-primary-foreground/15 hover:text-primary-foreground active:bg-primary-foreground/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-foreground/40"
                    >
                      <PlayIcon className="size-3" aria-hidden="true" />
                    </button>
                  }
                />
                <TooltipContent side="top" sideOffset={4}>
                  {t('bangQuery.rerunHint')}
                </TooltipContent>
              </Tooltip>
            ) : null}
          </div>
        ) : (
          <HighlightedText text={text} />
        )}
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
