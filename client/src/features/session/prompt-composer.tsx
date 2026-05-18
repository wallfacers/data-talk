"use client"

// 按钮多态与 chip abort 态遵循 client/DESIGN.md L282-288 (interaction tokens)
// 和 L330-338 (accessibility). uploading / sending 共用 disabled 视觉 (opacity-40 +
// cursor-not-allowed)，叠加 Loader2Icon spinner + aria-busy="true" 以满足 L335
// "state cannot be communicated by color alone". prefers-reduced-motion 下用 dot
// pulse 替代自旋（DESIGN.md L263 "Motion as Confirmation" + L337）。
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { ArrowUpIcon, Loader2Icon, Paperclip } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import {
  InputGroup,
  InputGroupAddon,
  InputGroupText,
  InputGroupTextarea,
} from '@/components/ui/input-group'
import { DataSourcePicker } from './data-source-picker/data-source-picker'
import { ModelPicker } from './model-picker/model-picker'
import { useSessionStore } from '@/stores/session-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useConnectionStore } from '@/features/connection/store'
import { useChannel } from '@/services/channel/use-channel'
import { createTextPart, createFileUploadPart } from '@/services/channel/types'
import { createSession, deleteSession } from '@/services/api/session'
import { normalizeError, showErrorToast } from '@/services/http-error'
import { StageToggleButton } from '@/features/stage/components/stage-toggle-button'
import { openDirectSqlQueryEditorTab } from '@/features/stage/utils/open-direct-sql-query-editor-tab'
import { createBangQueryMessage } from '@/services/api/bang-query-message'
import { useHasActiveModel } from './hooks/use-has-active-model'
import { useSessionDataContext } from './hooks/use-session-data-context'
import { invalidateSessionLists } from './hooks/use-sessions'
import { SQL_EXPLAIN_EVENT } from '@/features/chat/components/markdown/sql-code-block'
import { useI18n } from '@/i18n/use-i18n'
import { useDataSourcePickerStore } from './data-source-picker/data-source-picker-store'
import { cn } from '@/lib/utils'
import { shouldAutoRunDirectSql } from '@/features/stage/utils/direct-sql-auto-run-policy'
import { toast } from 'sonner'
import {
  IMAGE_DATA_URI_FETCH_TIMEOUT_MS,
  IMAGE_PAYLOAD_HARD_LIMIT_BYTES,
} from './constants'
import { classifyImagePayload, computeImagePayloadSize } from './image-payload'
import { evictDataUri, getDataUri, useFileUpload } from './useFileUpload'
import { FileAttachmentChip } from './components/file-attachment-chip'
import { FileDropZone } from './components/file-drop-zone'

function useComposerSlot(): HTMLElement | null {
  const [slot, setSlot] = useState<HTMLElement | null>(null)
  const slotRef = useRef<HTMLElement | null>(null)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  // Must mirror SplitView's hasMessages signal (hasEverSent || infoBySession).
  // During stream replay, message.created can arrive before message.part.created;
  // if we watch parts here, SplitView can swap the slot while the portal stays
  // attached to the removed DOM node.
  const hasEverSent = useSessionStore((s) =>
    activeSessionId ? (s.hasEverSentBySession.get(activeSessionId) ?? false) : false,
  )
  const hasStoreMessages = useChatPartsStore((s) => {
    const info = activeSessionId ? s.infoBySession.get(activeSessionId) : undefined
    return info ? info.size > 0 : false
  })
  const hasMessages = hasEverSent || hasStoreMessages

  useLayoutEffect(() => {
    const syncSlot = () => {
      const el = document.getElementById('composer-slot')
      if (slotRef.current === el) return
      slotRef.current = el
      setSlot(el)
    }

    syncSlot()
    const observer = new MutationObserver(syncSlot)
    observer.observe(document.body, { childList: true, subtree: true })
    return () => observer.disconnect()
  }, [activeSessionId, hasMessages])

  return slot
}

export function PromptComposer() {
  const slot = useComposerSlot()
  if (!slot) return null
  return createPortal(<InnerComposer />, slot)
}

function InnerComposer() {
  const { t } = useI18n()
  const { sendMessage, abort, isStreaming, canAbort } = useChannel()
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const openSession = useSessionStore((s) => s.openSession)
  const setPendingPrompt = useSessionStore((s) => s.setPendingPrompt)
  const composerRestoreDraft = useSessionStore((s) => s.composerRestoreDraft)
  const setComposerRestoreDraft = useSessionStore((s) => s.setComposerRestoreDraft)
  const composerDrafts = useSessionStore((s) => s.composerDrafts)
  const setComposerDraft = useSessionStore((s) => s.setComposerDraft)
  const hydrateComposerDraft = useSessionStore((s) => s.hydrateComposerDraft)
  const composerInsertText = useSessionStore((s) => s.composerInsertText)
  const setComposerInsertText = useSessionStore((s) => s.setComposerInsertText)
  const setPendingModelPrompt = useSessionStore((s) => s.setPendingModelPrompt)
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const setActiveConnection = useConnectionStore((s) => s.setActive)
  const hasActiveModel = useHasActiveModel()
  const sessionDataContext = useSessionDataContext(activeSessionId)
  const qc = useQueryClient()

  const {
    attachments,
    addFiles,
    removeAttachment,
    uploadAll,
    clearDone,
    hasUploads,
    hasInflight,
  } = useFileUpload(activeSessionId ?? '')

  // sendMessage 期间的本地 inflight 跟踪 — 与 hasInflight (upload) 区分，
  // 共同决定按钮多态（uploading / sending）。
  const [isSendInflight, setIsSendInflight] = useState(false)

  const handlePaste = useCallback((e: React.ClipboardEvent) => {
    const files = e.clipboardData.files
    if (files.length > 0) {
      e.preventDefault()
      addFiles(files)
    }
  }, [addFiles])

  const draftKey = activeSessionId ?? '__nosession__'

  const [text, setText] = useState(() => {
    if (composerDrafts[draftKey]) return composerDrafts[draftKey]
    const stored = hydrateComposerDraft(draftKey)
    return stored ?? ''
  })

  const isBangQueryMode = /^!\s*(select|with)\b/i.test(text.trim())

  const updateText = (value: string) => {
    setText(value)
    setComposerDraft(draftKey, value)
  }

  // When session switches without remount, restore the new session's draft
  const prevDraftKeyRef = useRef(draftKey)
  useEffect(() => {
    if (prevDraftKeyRef.current !== draftKey) {
      const stored = hydrateComposerDraft(draftKey)
      setText(composerDrafts[draftKey] ?? stored ?? '')
      prevDraftKeyRef.current = draftKey
    }
  }, [draftKey, composerDrafts, hydrateComposerDraft])

  useEffect(() => {
    if (!activeSessionId || !composerRestoreDraft) return
    if (composerRestoreDraft.sessionId !== activeSessionId) return

    setText((current) => current.trim().length > 0 ? current : composerRestoreDraft.text)
    setComposerRestoreDraft(null)
  }, [activeSessionId, composerRestoreDraft, setComposerRestoreDraft])

  const textareaRef = useRef<HTMLTextAreaElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    if (!composerInsertText) return
    if (!activeSessionId || composerInsertText.sessionId !== activeSessionId) return

    setText(composerInsertText.text)
    setComposerDraft(draftKey, composerInsertText.text)
    setComposerInsertText(null)

    // Focus the textarea after the next render
    requestAnimationFrame(() => {
      const el = textareaRef.current
      if (el) {
        el.focus()
        // Place cursor at end of text
        const len = el.value.length
        el.setSelectionRange(len, len)
      }
    })
  }, [activeSessionId, composerInsertText, setComposerInsertText, setComposerDraft, draftKey])

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
          showErrorToast(normalizeError(new Error(t('session.useTargetRequired'))))
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
            updateText(trimmed)
            showErrorToast(normalizeError(err))
            return
          }
        }

        if (!sessionId) return

        try {
          const resolved = await sessionDataContext.resolveUseTarget(target, sessionId)
          if (resolved.status !== 'matched' || !resolved.context) {
            const message = resolved.message ?? t('session.useTargetNotFound', { target })
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
          updateText('')
        } catch (err) {
          if (createdSessionId) {
            try {
              await deleteSession(createdSessionId)
              invalidateSessionLists(qc)
            } catch {
              // best-effort cleanup
            }
          }
          updateText(trimmed)
          showErrorToast(normalizeError(err))
        }
        return
      }

      const sql = trimmed.slice(1).trim()
      if (sql && /^(select|with)\b/i.test(sql)) {
        let connectionId = activeConnectionId
        if (!connectionId) {
          const picked = await useDataSourcePickerStore.getState().requestPick({
            reason: 'direct_sql',
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
            updateText(trimmed)
            showErrorToast(normalizeError(err))
            return
          }
        }
        const bangSessionId = sessionId ?? activeSessionId
        if (!bangSessionId) return

        try {
          updateText('')
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
          await openDirectSqlQueryEditorTab({
            sessionId: bangSessionId,
            connectionId,
            sql,
            autoRun: shouldAutoRunDirectSql(sql),
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
          updateText(trimmed) // restore user input on failure
        }
        return
      }
      // fall through to AI path for non-SELECT/WITH '!' content
    }

    if (!activeSessionId) {
      if (!hasActiveModel) {
        setPendingPrompt(trimmed)
        setPendingModelPrompt(true)
        return
      }
      updateText('')
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
        updateText(trimmed)
        showErrorToast(normalizeError(err))
      }
      return
    }

    updateText('')

    setIsSendInflight(true)
    try {
      // Eager upload (D4 in design.md) has typically drained pending → done by the
      // time the user hits Enter; uploadAll() returns immediately. The await stays
      // as a fallback for the (rare) race where Enter outpaces the in-flight uploads.
      const responses = attachments.length > 0 ? await uploadAll() : []

      // Build parts array with text + any completed file uploads. Image
      // attachments resolve their cached/in-flight base64 data URI here so the
      // backend can forward them as native OpenCode FileParts in the same
      // user message (no extra MCP read_file round-trip).
      const parts: unknown[] = [createTextPart(activeSessionId, trimmed)]
      const imageFileIds: string[] = []
      for (const r of responses) {
        let url: string | undefined
        if (r.mimeType?.startsWith('image/')) {
          const cached = await getDataUri(r.fileId, IMAGE_DATA_URI_FETCH_TIMEOUT_MS)
          if (cached) {
            url = cached
            imageFileIds.push(r.fileId)
          }
        }
        parts.push(createFileUploadPart(activeSessionId, r.fileId, r.filename, r.mimeType, r.sizeBytes, r.analysis as Record<string, unknown>, url))
      }

      // Size guard — base64 inflates ~33%, so >5MB combined payload puts the
      // OpenCode body / SSE frame at risk. Warn at 3MB, hard-reject at 5MB.
      const verdict = classifyImagePayload(computeImagePayloadSize(parts))
      if (verdict.kind === 'reject') {
        const sizeMB = (verdict.bytes / (1024 * 1024)).toFixed(1)
        const limitMB = (IMAGE_PAYLOAD_HARD_LIMIT_BYTES / (1024 * 1024)).toFixed(0)
        toast.error(t('chat.image.payloadTooLarge', { sizeMB, limitMB }))
        // Restore the draft so the user doesn't lose their text; chips stay so
        // they can remove some and retry. setPendingPrompt(null) is not needed
        // because we haven't entered the inflight branch yet.
        updateText(trimmed)
        return
      }
      if (verdict.kind === 'warn') {
        const sizeMB = (verdict.bytes / (1024 * 1024)).toFixed(1)
        toast.warning(t('chat.image.payloadWarning', { sizeMB }))
      }

      // Clear chips BEFORE awaiting sendMessage — its await spans the entire SSE
      // stream (AI streaming end), and gating clearDone behind it would keep the
      // chip on screen for the whole AI response window. The user bubble hydrates
      // from echoed FileParts (independent of local chip state), and a failed
      // send is owned by the pending bubble's retry path — neither needs the
      // chip preserved here. evictDataUri frees the cached base64; parts already
      // captured `url`, so eviction now is safe.
      clearDone()
      for (const fileId of imageFileIds) evictDataUri(fileId)

      await sendMessage(parts)
      // The failed pending user bubble owns retry; restoring here would also
      // re-persist the draft via setComposerDraft and resurrect on next CTRL+R.
    } finally {
      setIsSendInflight(false)
    }
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
  const updateTextRef = useRef(updateText)
  useEffect(() => {
    textRef.current = text
  }, [text])
  useEffect(() => {
    submitRef.current = submitText
  })
  useEffect(() => {
    updateTextRef.current = updateText
  })

  useEffect(() => {
    const onExplain = (e: Event) => {
      const detail = (e as CustomEvent).detail as { sql?: string } | undefined
      const sql = detail?.sql
      if (!sql) return
      const current = textRef.current
      const prefix = t('chat.explainSqlPrefix')
      const next = current ? current + '\n' + prefix + sql : prefix + sql
      updateTextRef.current(next)
      textRef.current = next
    }
    window.addEventListener(SQL_EXPLAIN_EVENT, onExplain)
    return () => {
      window.removeEventListener(SQL_EXPLAIN_EVENT, onExplain)
    }
  }, [])

  const canSend = (text.trim().length > 0 || attachments.length > 0) && !isStreaming && !hasUploads

  // Composer button state machine (see spec "发送按钮多态视觉反馈" + DESIGN.md L282-288):
  //   streaming  → destructive variant + spinner + abort
  //   sending    → disabled visual + spinner + aria-busy (sendMessage in flight)
  //   uploading  → disabled visual + spinner + aria-busy (any pending/uploading chip)
  //   idle       → primary background + ArrowUp icon
  // uploading and sending share visuals on purpose: the user perceives both as "the
  // message is being sent"; only aria-label disambiguates (screen reader semantics).
  type ComposerButtonState = 'idle' | 'uploading' | 'sending' | 'streaming'
  const composerButtonState: ComposerButtonState = isStreaming
    ? 'streaming'
    : isSendInflight
      ? 'sending'
      : hasInflight
        ? 'uploading'
        : 'idle'

  return (
    <form onSubmit={onSubmit} className="w-full">
      <FileDropZone onFiles={addFiles}>
      <InputGroup
        data-bang-query-mode={isBangQueryMode ? 'true' : undefined}
        className={cn(
          'rounded-2xl border-border/80 !border-border/80 !bg-bg-subtle/30 dark:!bg-bg-subtle/30 shadow-sm transition-all focus-within:!border-foreground/40 focus-within:shadow-md dark:!border-white/25 dark:focus-within:!border-white/40',
          isBangQueryMode && [
            'border-amber-500/45 !border-amber-500/45 !bg-amber-50/70 shadow-amber-950/5 focus-within:!border-amber-500/70 dark:!border-amber-400/40 dark:!bg-amber-950/20 dark:shadow-none',
          ],
        )}
      >
        {attachments.length > 0 && (
          <div
            className="flex flex-row gap-1.5 overflow-x-auto px-3 pt-3 pb-1"
            onMouseDown={(e) => e.stopPropagation()}
          >
            {attachments.map((a) => (
              <FileAttachmentChip
                key={a.id}
                attachment={a}
                onRemove={() => removeAttachment(a.id)}
              />
            ))}
          </div>
        )}
        <InputGroupTextarea
          ref={textareaRef}
          value={text}
          onChange={(e) => updateText(e.target.value)}
          onKeyDown={onKey}
          onPaste={handlePaste}
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
            <DataSourcePicker sessionId={activeSessionId} />

            {/* Stage 开关：手动打开/关闭右侧"电脑"窗体 */}
            <StageToggleButton />

            {/* Spacer */}
            <div className="flex-1" />

            {/* File upload button */}
            <Button
              type="button"
              variant="ghost"
              size="icon-xs"
              className="rounded-full text-text-muted"
              aria-label={t('chat.attachFiles')}
              onClick={() => fileInputRef.current?.click()}
            >
              <Paperclip className="size-3.5" />
            </Button>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              className="hidden"
              onChange={(e) => {
                if (e.target.files && e.target.files.length > 0) {
                  addFiles(e.target.files)
                  e.target.value = ''
                }
              }}
            />

            {/* Send / Stop button — state machine: idle | uploading | sending | streaming */}
            {composerButtonState === 'streaming' ? (
              <Button
                type="button"
                variant="destructive"
                size="icon-xs"
                className="rounded-full"
                disabled={!canAbort}
                aria-busy="true"
                onClick={() => {
                  if (!canAbort) return
                  void abort()
                }}
              >
                <Loader2Icon className="size-3.5 animate-spin motion-reduce:hidden" />
                <span
                  className="hidden motion-reduce:inline-block size-2 rounded-full bg-current opacity-60"
                  aria-hidden="true"
                />
              </Button>
            ) : composerButtonState === 'uploading' || composerButtonState === 'sending' ? (
              <Button
                type="button"
                size="icon-xs"
                className="rounded-full bg-primary text-primary-foreground opacity-40 cursor-not-allowed"
                aria-disabled="true"
                aria-busy="true"
                aria-label={
                  composerButtonState === 'uploading'
                    ? t('chat.composer.uploadingLabel')
                    : t('chat.composer.sendingLabel')
                }
                disabled
              >
                <Loader2Icon className="size-3.5 animate-spin motion-reduce:hidden text-text-inverse" />
                <span
                  className="hidden motion-reduce:inline-block size-2 rounded-full bg-current opacity-60"
                  aria-hidden="true"
                />
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
      </FileDropZone>
    </form>
  )
}
