"use client"

import { useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { ArrowUpIcon, Loader2Icon } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  InputGroup,
  InputGroupAddon,
  InputGroupText,
  InputGroupTextarea,
} from '@/components/ui/input-group'
import { DataSourcePicker } from './data-source-picker/data-source-picker'
import { ModelPicker } from './model-picker/model-picker'
import { Switch } from '@/components/ui/switch'
import { useSessionStore } from '@/stores/session-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useConnectionStore } from '@/features/connection/store'
import { useChannel } from '@/services/channel/use-channel'
import { createTextPart } from '@/services/channel/types'
import { createSession, deleteSession } from '@/services/api/session'
import { normalizeError, showErrorToast } from '@/services/http-error'
import { StageToggleButton } from '@/features/stage/components/stage-toggle-button'
import { openBangQueryTab } from '@/features/stage/utils/open-bang-query-tab'
import { createBangQueryMessage } from '@/services/api/bang-query-message'
import { useHasActiveModel } from './hooks/use-has-active-model'
import { useSessionDataContext } from './hooks/use-session-data-context'
import { invalidateSessionLists } from './hooks/use-sessions'
import { SQL_EXECUTE_EVENT, SQL_EXPLAIN_EVENT } from '@/features/chat/components/markdown/sql-code-block'
import { useI18n } from '@/i18n/use-i18n'
import { useDataSourcePickerStore } from './data-source-picker/data-source-picker-store'
import { cn } from '@/lib/utils'

function useComposerSlot(): HTMLElement | null {
  const [slot, setSlot] = useState<HTMLElement | null>(null)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  // Must mirror SplitView's hasMessages signal (hasEverSent || hasStoreMessages).
  // If we only watch chat-parts, enterSplit() flipping hasEverSent causes SplitView
  // to swap the composer-slot DOM node while our portal still points at the old one.
  const hasEverSent = useSessionStore((s) =>
    activeSessionId ? (s.hasEverSentBySession.get(activeSessionId) ?? false) : false,
  )
  const hasStoreMessages = useChatPartsStore((s) => {
    const parts = activeSessionId ? s.partsBySession.get(activeSessionId) : undefined
    return parts ? parts.size > 0 : false
  })
  const hasMessages = hasEverSent || hasStoreMessages

  useLayoutEffect(() => {
    const el = document.getElementById('composer-slot')
    if (el !== slot) setSlot(el)
  }, [activeSessionId, hasMessages, slot])

  return slot
}

export function PromptComposer() {
  const slot = useComposerSlot()
  if (!slot) return null
  return createPortal(<InnerComposer />, slot)
}

function InnerComposer() {
  const { t } = useI18n()
  const [text, setText] = useState('')
  const [autoMode, setAutoMode] = useState(true)
  const { sendMessage, abort, isStreaming } = useChannel()
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const openSession = useSessionStore((s) => s.openSession)
  const setPendingPrompt = useSessionStore((s) => s.setPendingPrompt)
  const composerRestoreDraft = useSessionStore((s) => s.composerRestoreDraft)
  const setComposerRestoreDraft = useSessionStore((s) => s.setComposerRestoreDraft)
  const setPendingModelPrompt = useSessionStore((s) => s.setPendingModelPrompt)
  const setPendingConnectionPrompt = useSessionStore((s) => s.setPendingConnectionPrompt)
  const setPendingActionAfterConnectionPick = useSessionStore((s) => s.setPendingActionAfterConnectionPick)
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const setActiveConnection = useConnectionStore((s) => s.setActive)
  const hasActiveModel = useHasActiveModel()
  const sessionDataContext = useSessionDataContext(activeSessionId)
  const qc = useQueryClient()
  const isBangQueryMode = /^!\s*(select|with)\b/i.test(text.trim())

  useEffect(() => {
    if (!activeSessionId || !composerRestoreDraft) return
    if (composerRestoreDraft.sessionId !== activeSessionId) return

    setText((current) => current.trim().length > 0 ? current : composerRestoreDraft.text)
    setComposerRestoreDraft(null)
  }, [activeSessionId, composerRestoreDraft, setComposerRestoreDraft])

  const submitText = async (raw: string) => {
    const trimmed = raw.trim()
    if (!trimmed || isStreaming) return

    // !<sql> direct-query intercept: bypass AI entirely for SELECT/WITH queries.
    // Other '!' prefixed content still routes to AI (compat with natural language use).
    if (trimmed.startsWith('!')) {
      const useMatch = /^!\s*use\b(?:\s+(.*))?$/i.exec(trimmed)
      if (useMatch) {
        const target = useMatch[1]?.trim() ?? ''
        if (!target) {
          showErrorToast(normalizeError(new Error('请输入要切换的数据源名称')))
          return
        }

        let sessionId = activeSessionId
        let createdSessionId: string | null = null
        if (!sessionId) {
          try {
            const initialTitle = trimmed.slice(0, 50)
            const sess = await createSession(activeConnectionId ?? undefined, initialTitle)
            createdSessionId = sess.id
            sessionId = sess.id
          } catch (err) {
            setText(trimmed)
            showErrorToast(normalizeError(err))
            return
          }
        }

        if (!sessionId) return

        try {
          const resolved = await sessionDataContext.resolveUseTarget(target, sessionId)
          if (resolved.status !== 'matched' || !resolved.context) {
            const message = resolved.message ?? `当前数据源下未找到 ${target}`
            throw new Error(message)
          }
          await sessionDataContext.setSessionDataContext({
            connectionId: resolved.context.connectionId,
            database: resolved.context.database,
            schema: resolved.context.schema,
            selectedLevel: resolved.context.selectedLevel,
          }, sessionId)
          if (createdSessionId) {
            openSession(sessionId, false)
            invalidateSessionLists(qc)
          }
          setText('')
        } catch (err) {
          if (createdSessionId) {
            try {
              await deleteSession(createdSessionId)
              invalidateSessionLists(qc)
            } catch {
              // best-effort cleanup
            }
          }
          setText(trimmed)
          showErrorToast(normalizeError(err))
        }
        return
      }

      const sql = trimmed.slice(1).trim()
      if (sql && /^(select|with)\b/i.test(sql)) {
        let connectionId = activeConnectionId
        if (!connectionId) {
          const picked = await useDataSourcePickerStore.getState().requestPick({
            reason: 'bang_query',
            preferredConnectionId: null,
          })
          if ('cancelled' in picked) return
          setActiveConnection(picked.connectionId)
          connectionId = picked.connectionId
        }

        let sessionId = activeSessionId
        let createdSessionId: string | null = null
        if (!sessionId) {
          try {
            // 使用用户输入的文本的前 50 个字符作为初始标题，实现标题快速填充
            const initialTitle = trimmed.slice(0, 50)
            const sess = await createSession(connectionId ?? undefined, initialTitle)
            createdSessionId = sess.id
            sessionId = sess.id
          } catch (err) {
            setText(trimmed)
            showErrorToast(normalizeError(err))
            return
          }
        }
        const bangSessionId = sessionId ?? activeSessionId
        if (!bangSessionId) return

        try {
          setText('')
          const createdAt = Date.now()
          const persisted = await createBangQueryMessage(bangSessionId, trimmed, createdAt)
          useChatPartsStore.getState().upsertInfo(bangSessionId, {
            id: persisted.id,
            role: 'user',
            sessionID: bangSessionId,
            time: { created: persisted.createdAt },
          })
          useChatPartsStore.getState().upsertPart(bangSessionId, {
            type: 'text',
            id: `prt_${persisted.id}`,
            sessionID: bangSessionId,
            messageID: persisted.id,
            text: trimmed,
            metadata: { displayKind: persisted.kind, queryMode: 'direct_sql' },
            synthetic: true,
          })
          openSession(bangSessionId, true)
          invalidateSessionLists(qc)
          void qc.invalidateQueries({ queryKey: ['session-history', 'messages', bangSessionId] })
          await openBangQueryTab({
            sessionId: bangSessionId,
            connectionId,
            sql,
          })
        } catch (err) {
          if (createdSessionId) {
            try {
              await deleteSession(createdSessionId)
              invalidateSessionLists(qc)
            } catch {
              // best-effort cleanup: keep the original error for the user
            }
          }
          showErrorToast(normalizeError(err))
          setText(trimmed) // restore user input on failure
        }
        return
      }
      // fall through to AI path for non-SELECT/WITH '!' content
    }

    if (!activeConnectionId) {
      setPendingPrompt(trimmed)
      setPendingConnectionPrompt(true)
      setPendingActionAfterConnectionPick({ kind: 'send' })
      const picked = await useDataSourcePickerStore.getState().requestPick({
        reason: 'send',
        preferredConnectionId: null,
      })
      if ('cancelled' in picked) {
        setPendingPrompt(null)
        setPendingConnectionPrompt(false)
        setPendingActionAfterConnectionPick(null)
        return
      }
      setActiveConnection(picked.connectionId)
      setPendingConnectionPrompt(false)
      return
    }

    if (!activeSessionId) {
      if (!hasActiveModel) {
        setPendingPrompt(trimmed)
        setPendingModelPrompt(true)
        return
      }
      setText('')
      setPendingPrompt(trimmed)
      try {
        // 使用用户输入的文本的前 50 个字符作为初始标题，实现标题快速填充
        const initialTitle = trimmed.slice(0, 50)
        const sess = await createSession(activeConnectionId ?? undefined, initialTitle)
        invalidateSessionLists(qc)
        // This session is being created specifically to send the pending prompt.
        // Open it directly in split/message mode so the composer does not flash
        // through the HERO slot before the resume hook submits the message.
        openSession(sess.id, true)
        // resume hook 会在 activeSessionId 就绪后消费 pendingPrompt
      } catch (err) {
        setPendingPrompt(null)
        setText(trimmed)
        showErrorToast(normalizeError(err))
      }
      return
    }

    setText('')
    const ok = await sendMessage([createTextPart(activeSessionId, trimmed)])
    if (!ok) setText(trimmed)
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    await submitText(text)
  }

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      void onSubmit(e as unknown as FormEvent)
    }
  }

  // 使 ref 同步最新值，供 window 事件 handler 避开闭包陷阱
  const textRef = useRef<string>('')
  const submitRef = useRef<(raw: string) => Promise<void>>(submitText)
  useEffect(() => {
    textRef.current = text
  }, [text])
  useEffect(() => {
    submitRef.current = submitText
  })

  useEffect(() => {
    const onExecute = (e: Event) => {
      const detail = (e as CustomEvent).detail as { sql?: string } | undefined
      const sql = detail?.sql
      if (!sql) return
      const current = textRef.current
      if (!current.trim()) {
        // composer 空：填入并自动 submit（用 override 绕过 stale state）
        setText(sql)
        textRef.current = sql
        queueMicrotask(() => {
          void submitRef.current(sql)
        })
      } else {
        // 非空：追加，不 submit
        const next = current.endsWith('\n') ? current + sql : current + '\n' + sql
        setText(next)
        textRef.current = next
        toast(t('chat.appendSql'))
      }
    }
    const onExplain = (e: Event) => {
      const detail = (e as CustomEvent).detail as { sql?: string } | undefined
      const sql = detail?.sql
      if (!sql) return
      const current = textRef.current
      const prefix = t('chat.explainSqlPrefix')
      const next = current ? current + '\n' + prefix + sql : prefix + sql
      setText(next)
      textRef.current = next
    }
    window.addEventListener(SQL_EXECUTE_EVENT, onExecute)
    window.addEventListener(SQL_EXPLAIN_EVENT, onExplain)
    return () => {
      window.removeEventListener(SQL_EXECUTE_EVENT, onExecute)
      window.removeEventListener(SQL_EXPLAIN_EVENT, onExplain)
    }
  }, [])

  const canSend = text.trim().length > 0 && !isStreaming

  return (
    <form onSubmit={onSubmit} className="w-full">
      <InputGroup
        data-bang-query-mode={isBangQueryMode ? 'true' : undefined}
        className={cn(
          'rounded-2xl !border-foreground/20 shadow-sm transition-all focus-within:!border-foreground/40 focus-within:shadow-md dark:!border-white/25 dark:focus-within:!border-white/40',
          isBangQueryMode && [
            '!border-amber-500/50 bg-amber-50/70 shadow-amber-950/5 focus-within:!border-amber-500/70 dark:!border-amber-400/40 dark:bg-amber-950/20 dark:shadow-none',
          ],
        )}
      >
        <InputGroupTextarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKey}
          placeholder={t('chat.promptPlaceholder')}
          className={cn(
            'h-[90px] resize-none overflow-y-auto px-4 py-4 text-base leading-relaxed text-black dark:text-white [&::-webkit-scrollbar-track]:my-3',
            isBangQueryMode && 'text-amber-900 placeholder:text-amber-700/60 dark:text-amber-100 dark:placeholder:text-amber-200/55',
          )}
          rows={3}
        />
        <InputGroupAddon align="block-end" className="pt-2">
          <div className="flex w-full items-center gap-2">
            {isBangQueryMode && (
              <InputGroupText className="rounded-full border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 text-[11px] font-medium text-amber-700 dark:border-amber-400/30 dark:bg-amber-400/10 dark:text-amber-200">
                {t('chat.directQueryMode')}
              </InputGroupText>
            )}
            {/* Model selector */}
            <ModelPicker />

            {/* Data source selector */}
            <DataSourcePicker />

            {/* Auto toggle */}
            <InputGroupText
              className="cursor-pointer gap-1.5 text-xs"
              style={{ color: 'var(--foreground)' }}
              onClick={(e) => {
                e.stopPropagation()
                if ((e.target as HTMLElement).closest('[data-slot=switch]')) return
                setAutoMode((v) => !v)
              }}
            >
              <Switch
                size="sm"
                checked={autoMode}
                onCheckedChange={setAutoMode}
                className="data-[size=sm]:h-[14px] data-[size=sm]:w-[24px]"
              />
              {t('session.autoMode')}
            </InputGroupText>

            {/* Stage 开关：手动打开/关闭右侧"电脑"窗体 */}
            <StageToggleButton />

            {/* Spacer */}
            <div className="flex-1" />

            {/* Send / Stop button */}
            {isStreaming ? (
              <Button
                type="button"
                variant="destructive"
                size="icon-xs"
                className="rounded-full"
                onClick={() => void abort()}
              >
                <Loader2Icon className="size-3.5 animate-spin" />
              </Button>
            ) : (
              <Button
                type="submit"
                size="icon-xs"
                data-disabled={!canSend || undefined}
                aria-disabled={!canSend}
                className="rounded-full bg-primary text-primary-foreground hover:bg-primary/90 data-disabled:opacity-40"
              >
                <ArrowUpIcon className="size-3.5" />
              </Button>
            )}
          </div>
        </InputGroupAddon>
      </InputGroup>
    </form>
  )
}
