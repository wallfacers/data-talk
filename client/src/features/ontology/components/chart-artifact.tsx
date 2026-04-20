import { useMemo, useRef, useEffect } from 'react'
import { LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'
import { echartsOptionToChartSpec } from '../echarts-to-recharts'
import type { Artifact } from '@/services/channel/event-reducer'
import { useI18n } from '@/i18n/use-i18n'

export function ChartArtifact({ artifact }: { artifact: Artifact }) {
  const { t } = useI18n()
  const spec = useMemo(() =>
    echartsOptionToChartSpec((artifact.payload as any)?.echartsOption),
    [artifact.payload]
  )
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    ref.current?.animate([{ opacity: 0.6 }, { opacity: 1 }],
      { duration: 180, fill: 'forwards' })
  }, [artifact.id, artifact.version])

  if (spec.type === 'unsupported') {
    return <div className="p-4 text-xs text-muted-foreground">{t('artifact.unsupportedChart')}</div>
  }

  const color = spec.colors?.[0] ?? '#3b82f6'

  return (
    <div ref={ref} className="h-full w-full overflow-hidden min-h-0 min-w-0 [&_.recharts-cartesian-axis-tick_text]:fill-muted-foreground [&_.recharts-cartesian-grid_line[stroke='#ccc']]:stroke-border [&_.recharts-curve.recharts-tooltip-cursor]:stroke-border [&_.recharts-dot[stroke='#fff']]:stroke-transparent [&_.recharts-layer]:outline-hidden [&_.recharts-polar-grid_[stroke='#ccc']]:stroke-border [&_.recharts-radial-bar-background-sector]:fill-muted [&_.recharts-rectangle.recharts-tooltip-cursor]:fill-muted [&_.recharts-reference-line_[stroke='#ccc']]:stroke-border [&_.recharts-sector]:outline-hidden [&_.recharts-sector[stroke='#fff']]:stroke-transparent [&_.recharts-surface]:outline-hidden">
      <ResponsiveContainer width="100%" height="100%">
        {spec.type === 'line' ? (
          <LineChart data={toRechartsData(spec)}>
            <XAxis dataKey="x" />
            <YAxis />
            <Tooltip 
              contentStyle={{ backgroundColor: 'var(--popover)', color: 'var(--popover-foreground)', borderColor: 'var(--border)', borderRadius: '6px' }}
              itemStyle={{ color: 'var(--popover-foreground)' }}
            />
            {spec.series.map((_, i) => (
              <Line key={i} type="monotone" dataKey={`s${i}`} stroke={color} dot={false} />
            ))}
          </LineChart>
        ) : spec.type === 'bar' ? (
          <BarChart data={toRechartsData(spec)}>
            <XAxis dataKey="x" />
            <YAxis />
            <Tooltip 
              contentStyle={{ backgroundColor: 'var(--popover)', color: 'var(--popover-foreground)', borderColor: 'var(--border)', borderRadius: '6px' }}
              itemStyle={{ color: 'var(--popover-foreground)' }}
            />
            {spec.series.map((_, i) => <Bar key={i} dataKey={`s${i}`} fill={color} />)}
          </BarChart>
        ) : (
          <PieChart>
            <Pie data={spec.data} dataKey="value" nameKey="name" outerRadius={120}>
              {spec.data.map((_, i) => <Cell key={i} fill={spec.colors?.[i % (spec.colors?.length ?? 1)] ?? color} />)}
            </Pie>
            <Tooltip 
              contentStyle={{ backgroundColor: 'var(--popover)', color: 'var(--popover-foreground)', borderColor: 'var(--border)', borderRadius: '6px' }}
              itemStyle={{ color: 'var(--popover-foreground)' }}
            />
          </PieChart>
        )}
      </ResponsiveContainer>
    </div>
  )
}

function toRechartsData(s: Extract<ReturnType<typeof echartsOptionToChartSpec>, { type: 'line'|'bar' }>) {
  return (s.xData ?? []).map((x, i) => {
    const row: any = { x }
    s.series.forEach((ss, si) => { row[`s${si}`] = ss.data[i] })
    return row
  })
}
