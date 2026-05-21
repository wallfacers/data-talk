// QuestionDock 取代 composer 呈现 AI 的结构化提问（client/DESIGN.md L222-225, L313）。
// 选中态带 radio dot / check 标记，不靠颜色（L288, L335）；accent.primary 仅用于 Submit
// 与选中强调（L279, L353）；键盘全覆盖（L336）；prefers-reduced-motion 退化（L337）。
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Check } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import { replyQuestion, rejectQuestion, type QuestionRequest } from '@/services/api/question'
import { useQuestionStore } from '@/stores/question-store'
import { normalizeError, showErrorToast } from '@/services/http-error'

// Per-request draft cache so navigating away and back (or a transient remount)
// keeps the user's in-progress answers. Cleared once the request resolves.
type Draft = { tab: number; answers: string[][]; custom: string[]; customOn: boolean[] }
const draftCache = new Map<string, Draft>()

function Mark({ multi, picked }: { multi: boolean; picked: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'mt-0.5 flex size-4 shrink-0 items-center justify-center border',
        multi ? 'rounded-[4px]' : 'rounded-full',
        picked ? 'border-primary bg-primary text-primary-foreground' : 'border-border bg-bg-canvas',
      )}
    >
      {picked && (multi
        ? <Check className="size-3" />
        : <span className="size-1.5 rounded-full bg-current" />)}
    </span>
  )
}

export function QuestionDock({
  sessionId,
  request,
}: {
  sessionId: string
  request: QuestionRequest
}) {
  const { t } = useI18n()
  const questions = request.questions
  const total = questions.length

  const cached = draftCache.get(request.id)
  const [tab, setTab] = useState(cached?.tab ?? 0)
  const [answers, setAnswers] = useState<string[][]>(cached?.answers ?? [])
  const [custom, setCustom] = useState<string[]>(cached?.custom ?? [])
  const [customOn, setCustomOn] = useState<boolean[]>(cached?.customOn ?? [])
  const [editing, setEditing] = useState(false)
  const [sending, setSending] = useState(false)

  const question = questions[tab]
  const options = question?.options ?? []
  const multi = question?.multiple === true
  const allowCustom = question?.custom !== false
  const isLast = tab >= total - 1

  const rootRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    return () => {
      // Persist draft on unmount unless the request already resolved (removed from store).
      if (useQuestionStore.getState().bySession.get(sessionId)?.some((q) => q.id === request.id)) {
        draftCache.set(request.id, { tab, answers, custom, customOn })
      }
    }
  }, [sessionId, request.id, tab, answers, custom, customOn])

  const setAnswerAt = useCallback((i: number, next: string[]) => {
    setAnswers((prev) => {
      const copy = prev.slice()
      copy[i] = next
      return copy
    })
  }, [])

  const setCustomAt = useCallback((i: number, value: string) => {
    setCustom((prev) => {
      const copy = prev.slice()
      copy[i] = value
      return copy
    })
  }, [])

  const setCustomOnAt = useCallback((i: number, value: boolean) => {
    setCustomOn((prev) => {
      const copy = prev.slice()
      copy[i] = value
      return copy
    })
  }, [])

  const resolved = () => {
    draftCache.delete(request.id)
    // Optimistic local removal — the question.replied/rejected event also removes,
    // but clearing now avoids a flash if the event lags.
    useQuestionStore.getState().removeByRequestId(sessionId, request.id)
  }

  const submitAnswers = useCallback(async (finalAnswers: string[][]) => {
    if (sending) return
    setSending(true)
    try {
      await replyQuestion(request.id, finalAnswers)
      resolved()
    } catch (err) {
      setSending(false)
      showErrorToast(normalizeError(err))
    }
  }, [sending, request.id]) // eslint-disable-line react-hooks/exhaustive-deps

  const submit = useCallback(() => {
    const finalAnswers = questions.map((_, i) => answers[i] ?? [])
    void submitAnswers(finalAnswers)
  }, [questions, answers, submitAnswers])

  const reject = useCallback(async () => {
    if (sending) return
    setSending(true)
    try {
      await rejectQuestion(request.id)
      resolved()
    } catch (err) {
      setSending(false)
      showErrorToast(normalizeError(err))
    }
  }, [sending, request.id]) // eslint-disable-line react-hooks/exhaustive-deps

  const next = useCallback(() => {
    if (sending) return
    if (isLast) { submit(); return }
    setTab((tab) => Math.min(total - 1, tab + 1))
    setEditing(false)
  }, [sending, isLast, submit, total])

  const back = useCallback(() => {
    if (sending || tab <= 0) return
    setTab((tab) => Math.max(0, tab - 1))
    setEditing(false)
  }, [sending, tab])

  const pickOption = (label: string) => {
    if (sending) return
    setCustomOnAt(tab, false)
    setEditing(false)
    if (multi) {
      const current = answers[tab] ?? []
      setAnswerAt(tab, current.includes(label)
        ? current.filter((l) => l !== label)
        : [...current, label])
      return
    }
    // single-select: set the answer; submission always goes through the Submit button
    // (or Cmd/Ctrl+Enter), matching OpenCode — never auto-submit on pick.
    setAnswerAt(tab, [label])
  }

  const toggleCustom = () => {
    if (sending) return
    const turningOn = !(customOn[tab] === true)
    setCustomOnAt(tab, turningOn)
    setEditing(turningOn)
    if (!multi && turningOn) {
      // single-select: choosing custom clears option picks
      setAnswerAt(tab, custom[tab]?.trim() ? [custom[tab].trim()] : [])
    }
    if (!turningOn) {
      const value = (custom[tab] ?? '').trim()
      if (value) setAnswerAt(tab, (answers[tab] ?? []).filter((l) => l !== value))
    }
  }

  const onCustomInput = (value: string) => {
    const prev = (custom[tab] ?? '').trim()
    setCustomAt(tab, value)
    if (customOn[tab] !== true) return
    const nextVal = value.trim()
    if (multi) {
      const base = prev ? (answers[tab] ?? []).filter((l) => l !== prev) : (answers[tab] ?? [])
      setAnswerAt(tab, nextVal && !base.includes(nextVal) ? [...base, nextVal] : base)
      return
    }
    setAnswerAt(tab, nextVal ? [nextVal] : [])
  }

  const picked = (label: string) => (answers[tab] ?? []).includes(label)
  const customSelected = customOn[tab] === true

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      void reject()
      return
    }
    const mod = (e.metaKey || e.ctrlKey) && !e.altKey
    if (mod && e.key === 'Enter') {
      e.preventDefault()
      next()
    }
  }

  const progressLabel = useMemo(
    () => t('session.question.progress', { current: Math.min(tab + 1, total), total }),
    [t, tab, total],
  )

  return (
    <div
      ref={rootRef}
      data-component="question-dock"
      onKeyDown={onKeyDown}
      className={cn(
        'flex w-full flex-col gap-3 rounded-2xl border border-border bg-bg-panel p-4 shadow-sm',
        'focus-within:border-foreground/40',
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium text-text-muted">{progressLabel}</span>
        {total > 1 && (
          <div className="flex items-center gap-1.5" role="tablist" aria-label={t('ui.tool.questions')}>
            {questions.map((_, i) => {
              const answered = (answers[i]?.length ?? 0) > 0
              return (
                <button
                  key={i}
                  type="button"
                  role="tab"
                  aria-selected={i === tab}
                  aria-label={`${t('ui.tool.questions')} ${i + 1}`}
                  disabled={sending}
                  onClick={() => { setTab(i); setEditing(false) }}
                  className={cn(
                    'size-2 rounded-full transition-colors motion-reduce:transition-none',
                    i === tab ? 'bg-primary' : answered ? 'bg-foreground/40' : 'bg-border',
                  )}
                />
              )
            })}
          </div>
        )}
      </div>

      <div className="text-sm font-medium text-foreground">{question?.question}</div>
      <p className="text-xs text-text-muted">
        {multi ? t('session.question.multiHint') : t('session.question.singleHint')}
      </p>

      <div role={multi ? 'group' : 'radiogroup'} className="flex flex-col gap-1.5">
        {options.map((opt) => {
          const isPicked = picked(opt.label)
          return (
            <button
              key={opt.label}
              type="button"
              role={multi ? 'checkbox' : 'radio'}
              aria-checked={isPicked}
              disabled={sending}
              onClick={() => pickOption(opt.label)}
              className={cn(
                'flex items-start gap-2.5 rounded-lg border px-3 py-2 text-left transition-colors motion-reduce:transition-none',
                'outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50',
                isPicked ? 'border-primary bg-primary/5' : 'border-border hover:bg-accent',
              )}
            >
              <Mark multi={multi} picked={isPicked} />
              <span className="flex flex-col gap-0.5">
                <span className="text-sm text-foreground">{opt.label}</span>
                {opt.description && <span className="text-xs text-text-muted">{opt.description}</span>}
              </span>
            </button>
          )
        })}

        {allowCustom && (
          <div
            className={cn(
              'flex items-start gap-2.5 rounded-lg border px-3 py-2 transition-colors motion-reduce:transition-none',
              customSelected ? 'border-primary bg-primary/5' : 'border-border',
            )}
          >
            <button
              type="button"
              role={multi ? 'checkbox' : 'radio'}
              aria-checked={customSelected}
              aria-label={t('ui.messagePart.option.typeOwnAnswer')}
              disabled={sending}
              onClick={toggleCustom}
              className="outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
            >
              <Mark multi={multi} picked={customSelected} />
            </button>
            <div className="flex flex-1 flex-col gap-1">
              <span className="text-sm text-foreground">{t('ui.messagePart.option.typeOwnAnswer')}</span>
              {(customSelected || editing) ? (
                <textarea
                  autoFocus
                  rows={1}
                  value={custom[tab] ?? ''}
                  placeholder={t('ui.question.custom.placeholder')}
                  disabled={sending}
                  onChange={(e) => onCustomInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey && !(e.metaKey || e.ctrlKey)) {
                      e.preventDefault()
                      setEditing(false)
                    }
                  }}
                  className="w-full resize-none rounded-md border border-border bg-bg-canvas px-2 py-1 text-sm text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring"
                />
              ) : (
                <button
                  type="button"
                  disabled={sending}
                  onClick={toggleCustom}
                  className="text-left text-xs text-text-muted"
                >
                  {custom[tab]?.trim() || t('ui.question.custom.placeholder')}
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      <div className="flex items-center justify-between gap-2 pt-1">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={sending}
          onClick={() => void reject()}
          aria-keyshortcuts="Escape"
        >
          {t('ui.common.dismiss')}
        </Button>
        <div className="flex items-center gap-2">
          {tab > 0 && (
            <Button type="button" variant="secondary" size="sm" disabled={sending} onClick={back}>
              {t('ui.common.back')}
            </Button>
          )}
          <Button
            type="button"
            variant={isLast ? 'default' : 'secondary'}
            size="sm"
            disabled={sending}
            onClick={next}
            aria-keyshortcuts="Meta+Enter Control+Enter"
          >
            {isLast ? t('ui.common.submit') : t('ui.common.next')}
          </Button>
        </div>
      </div>
    </div>
  )
}
