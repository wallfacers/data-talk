import { cn } from '@/lib/utils'

const FALLBACK = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 9h6v6H9z"/></svg>`

const ICONS = import.meta.glob<string>('@/assets/icons/provider/*.svg', { eager: true, query: '?raw', import: 'default' })

const ICON_MAP: Record<string, string> = {}
for (const [path, svg] of Object.entries(ICONS)) {
  const id = path.match(/provider\/(.+)\.svg$/)?.[1]
  if (id) ICON_MAP[id] = svg as string
}

export function ProviderIcon({ id, className }: { id: string; className?: string }) {
  const svg = ICON_MAP[id] ?? FALLBACK
  return (
    <div
      className={cn('size-5 text-foreground flex-shrink-0 [&>svg]:size-full', className)}
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  )
}
