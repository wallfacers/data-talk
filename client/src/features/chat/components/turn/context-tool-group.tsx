import { useState } from 'react'
import type { ToolPart, MessageInfo } from '@/services/channel/types'
import { ToolPart as ToolPartRenderer } from './tool-part'
import { AnimatedCount } from '../effects/animated-count'
import { TextShimmer } from '../effects/text-shimmer'

export function ContextToolGroup(props: { parts: ToolPart[]; infos: Map<string, MessageInfo>; busy: boolean }) {
  const [open, setOpen] = useState(false)
  const count = props.parts.length

  return (
    <div className="my-2 rounded border">
      <button onClick={() => setOpen(!open)} className="flex w-full items-center justify-between px-3 py-2 text-left text-sm">
        <span>
          <TextShimmer text={props.busy ? '收集上下文中…' : '已收集上下文'} active={props.busy} />
          <span className="ml-2 text-xs text-muted-foreground">· <AnimatedCount value={count} /> 项</span>
        </span>
        <span className={open ? 'rotate-180 transition-transform' : 'transition-transform'}>▼</span>
      </button>
      {open && (
        <div className="border-t">
          {props.parts.map((p) => (
            <ToolPartRenderer key={p.id} part={p} info={props.infos.get(p.messageID)!} />
          ))}
        </div>
      )}
    </div>
  )
}
