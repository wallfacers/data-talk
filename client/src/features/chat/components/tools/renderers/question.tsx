import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'

type SubQuestion = { question?: string; header?: string }

export function Question(props: ToolRendererProps) {
  const { part } = props
  const status = part.state.status
  // pending/running 时整个 part 不渲染（由 composer 的 QuestionDock 接管对话）
  if (status === 'pending' || status === 'running') return null

  const lang = getCurrentLanguage()
  const input = part.state.input as { questions?: SubQuestion[]; question?: string } | undefined
  // 兼容多子问题（新）与单问题（旧）两种 input 形状
  const questions: SubQuestion[] = input?.questions?.length
    ? input.questions
    : input?.question
      ? [{ question: input.question }]
      : []
  // 后端透传 OpenCode tool metadata.answers = string[][]（按问题顺序，每项为选中 label 数组）
  const answers = (part.state.metadata?.answers as string[][] | undefined) ?? []

  const unanswered = translateMessage(lang, 'session.question.unanswered')
  const title = questions.length === 1
    ? (questions[0].question || translateMessage(lang, 'common.question'))
    : translateMessage(lang, 'session.question.askedCount', { count: questions.length })

  return (
    <BasicTool
      icon="bubble"
      variant="question"
      status={status}
      trigger={{ title }}
      defaultOpen
    >
      <div className="flex flex-col gap-2">
        {questions.map((q, i) => {
          const answer = answers[i]?.length ? answers[i].join(', ') : unanswered
          return (
            <div key={i} className="flex flex-col gap-0.5">
              {questions.length > 1 && q.question && (
                <div className="text-xs text-text-muted">{q.question}</div>
              )}
              <div className="text-sm text-foreground">{answer}</div>
            </div>
          )
        })}
      </div>
    </BasicTool>
  )
}
