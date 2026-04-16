import { useMemo, useRef, useEffect } from 'react'
import { LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'
import { echartsOptionToChartSpec } from '../echarts-to-recharts'
import type { Artifact } from '@/services/channel/event-reducer'

export function ChartArtifact({ artifact }: { artifact: Artifact }) {
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
    return <div className="p-4 text-xs text-muted-foreground">不支持的图表规格</div>
  }

  const color = spec.colors?.[0] ?? '#3b82f6'

  return (
    <div ref={ref} className="h-full w-full">
      <ResponsiveContainer>
        {spec.type === 'line' ? (
          <LineChart data={toRechartsData(spec)}>
            <XAxis dataKey="x" />
            <YAxis />
            <Tooltip />
            {spec.series.map((_, i) => (
              <Line key={i} type="monotone" dataKey={`s${i}`} stroke={color} dot={false} />
            ))}
          </LineChart>
        ) : spec.type === 'bar' ? (
          <BarChart data={toRechartsData(spec)}>
            <XAxis dataKey="x" />
            <YAxis />
            <Tooltip />
            {spec.series.map((_, i) => <Bar key={i} dataKey={`s${i}`} fill={color} />)}
          </BarChart>
        ) : (
          <PieChart>
            <Pie data={spec.data} dataKey="value" nameKey="name" outerRadius={120}>
              {spec.data.map((_, i) => <Cell key={i} fill={spec.colors?.[i % (spec.colors?.length ?? 1)] ?? color} />)}
            </Pie>
            <Tooltip />
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
