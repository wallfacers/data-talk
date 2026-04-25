import { useMemo } from 'react'
import { ChartRenderer } from '@/features/chat/components/markdown/chart-renderer'
import type { Artifact } from '@/services/channel/event-reducer'
import { useI18n } from '@/i18n/use-i18n'

export function ChartArtifact({ artifact }: { artifact: Artifact }) {
  const { t } = useI18n()

  const option = useMemo(() => {
    const payload = artifact.payload as { echartsOption?: unknown } | undefined
    const echartsOption = payload?.echartsOption
    if (!echartsOption || typeof echartsOption !== 'object') return null
    return echartsOption as Record<string, unknown>
  }, [artifact.payload, artifact.id, artifact.version])

  if (!option) {
    return (
      <div className="p-4 text-xs text-[var(--dt-text-muted)]">
        {t('artifact.missingChartOption')}
      </div>
    )
  }

  return (
    <div className="w-full overflow-hidden">
      <ChartRenderer option={option} height={420} showAspectBadge />
    </div>
  )
}
