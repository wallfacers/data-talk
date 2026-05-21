import { useState } from 'react'
import type { ToolPart, MessageInfo } from '@/services/channel/types'
import { ToolPart as ToolPartRenderer } from './tool-part'
import { AnimatedCount } from '../effects/animated-count'
import { TextShimmer } from '../effects/text-shimmer'
import { useI18n } from '@/i18n/use-i18n'

export function ContextToolGroup(props: { parts: ToolPart[]; infos: Map<string, MessageInfo>; busy: boolean }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const count = props.parts.length

  return (
    <div className="my-2 rounded border">
      <button onClick={() => setOpen(!open)} className="flex w-full items-center justify-between px-3 py-2 text-left text-sm">
        <span>
          <TextShimmer text={props.busy ? t('chat.contextCollecting') : t('chat.contextCollected')} active={props.busy} />
          <span className="ml-2 text-xs text-muted-foreground">· <AnimatedCount value={count} /> {t('chat.itemUnit')}</span>
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
