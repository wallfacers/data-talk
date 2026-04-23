import { useEffect, useMemo, useRef, useState } from 'react'
import { ChartRenderer } from '@/features/chat/components/markdown/chart-renderer'
import type { Artifact } from '@/services/channel/event-reducer'
import { useI18n } from '@/i18n/use-i18n'

const FALLBACK_HEIGHT = 320

export function ChartArtifact({ artifact }: { artifact: Artifact }) {
  const { t } = useI18n()
  const hostRef = useRef<HTMLDivElement>(null)
  const [height, setHeight] = useState(FALLBACK_HEIGHT)

  const option = useMemo(() => {
    const payload = artifact.payload as { echartsOption?: unknown } | undefined
    const echartsOption = payload?.echartsOption
    if (!echartsOption || typeof echartsOption !== 'object') return null
    return echartsOption as Record<string, unknown>
  }, [artifact.payload, artifact.id, artifact.version])

  useEffect(() => {
    const host = hostRef.current
    if (!host) return

    const updateHeight = () => {
      const next = Math.max(220, Math.floor(host.clientHeight || FALLBACK_HEIGHT))
      setHeight(next)
    }

    updateHeight()
    if (typeof ResizeObserver === 'undefined') return

    const observer = new ResizeObserver(() => updateHeight())
    observer.observe(host)
    return () => observer.disconnect()
  }, [])

  if (!option) {
    return (
      <div className="p-4 text-xs text-[var(--dt-text-muted)]">
        {t('artifact.missingChartOption')}
      </div>
    )
  }

  return (
    <div ref={hostRef} className="h-full w-full min-h-0 min-w-0 overflow-hidden">
      <ChartRenderer option={option} height={height} />
    </div>
  )
}
