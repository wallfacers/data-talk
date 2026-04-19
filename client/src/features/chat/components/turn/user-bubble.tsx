import { useState } from 'react'
import type { MessageInfo, Part, TextPart } from '@/services/channel/types'
import { useChannel } from '@/services/channel/use-channel'
import { cn } from '@/lib/utils'

function HighlightedText(props: { text: string }) {
  return <>{props.text}</>
}

export function UserBubble(props: { info: MessageInfo; parts: Part[] }) {
  const { info, parts } = props
  const text = (parts.find((p) => p.type === 'text') as TextPart | undefined)?.text ?? ''
  const [copied, setCopied] = useState(false)
  const channel = useChannel()

  const pending = !!info.__pending
  const failed = !!info.__failed
  const retrying = !!info.__retrying

  const handleCopy = async () => {
    await navigator.clipboard?.writeText?.(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
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
        'max-w-[85%] rounded-lg px-3 py-2 text-sm',
        'bg-primary text-primary-foreground',
        pending && !failed && 'opacity-85',
        failed && 'border-2 border-red-500',
      )}>
        <HighlightedText text={text} />
        {retrying && <span className="ml-2 inline-block animate-spin">⟳</span>}
      </div>
      {failed && (
        <div className="flex gap-2 text-xs">
          <span className="text-red-500">⚠ 发送失败：{info.__failReason}</span>
          <button onClick={handleRetry} className="text-primary hover:underline">重试</button>
          <button onClick={handleRemove} className="text-muted-foreground hover:underline">删除</button>
        </div>
      )}
      {!pending && !failed && (
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <button onClick={handleCopy} className="hover:text-foreground">{copied ? '✓' : '复制'}</button>
          {info.time.created && <span>· {new Date(info.time.created).toLocaleTimeString()}</span>}
        </div>
      )}
    </div>
  )
}
