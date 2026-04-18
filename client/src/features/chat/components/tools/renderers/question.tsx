import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'

export function Question(props: ToolRendererProps) {
  const { part } = props
  const status = part.state.status
  // pending/running 时整个 part 不渲染（由 composer 处理对话）
  if (status === 'pending' || status === 'running') return null

  const question = (part.state.input?.question as string | undefined) ?? ''
  const rawAnswer = part.state.output as { answer?: string } | string | undefined
  const answerText =
    typeof rawAnswer === 'string' ? rawAnswer : rawAnswer?.answer ?? ''

  return (
    <BasicTool
      icon="bubble"
      variant="question"
      status={status}
      trigger={{ title: question || '问题' }}
      defaultOpen
    >
      {answerText && <div className="text-sm">{answerText}</div>}
    </BasicTool>
  )
}
