import { WidgetShell } from './widget-shell'
import { Markdown } from '@/features/chat/components/markdown/markdown'

interface MarkdownWidgetProps {
  widgetId: string
  title?: string
  text: string
}

export function MarkdownWidget({ widgetId, title, text }: MarkdownWidgetProps) {
  return (
    <WidgetShell title={title}>
      <Markdown text={text} cacheKey={`dashboard:${widgetId}`} />
    </WidgetShell>
  )
}
