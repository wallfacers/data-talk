import { useState } from 'react'
import { ChevronDownIcon, ChevronRightIcon } from 'lucide-react'

type Props = {
  label: string
  count: number
  defaultOpen?: boolean
  children: React.ReactNode
}

export function StageRailGroup({ label, count, defaultOpen = true, children }: Props) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <div className="flex flex-col">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={[
          'flex h-7 items-center gap-1.5 px-1 text-[11px] font-medium uppercase tracking-wide text-text-soft',
          'hover:text-text-base',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing rounded',
        ].join(' ')}
      >
        {open ? <ChevronDownIcon className="size-3" /> : <ChevronRightIcon className="size-3" />}
        <span className="flex-1 text-left">{label}</span>
        <span className="text-text-soft tabular-nums">{count}</span>
      </button>
      {open ? <ul role="list" className="flex flex-col gap-0.5 px-1 pb-1">{children}</ul> : null}
    </div>
  )
}
