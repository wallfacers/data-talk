export type ChartSpec =
  | { type: 'line', xData: (string|number)[], series: { name?: string, data: number[] }[], colors?: string[] }
  | { type: 'bar',  xData: (string|number)[], series: { name?: string, data: number[] }[], colors?: string[] }
  | { type: 'pie',  data: { name: string, value: number }[], colors?: string[] }
  | { type: 'unsupported' }

export function echartsOptionToChartSpec(opt: any): ChartSpec {
  try {
    const first = (opt.series ?? [])[0]
    if (!first) return { type: 'unsupported' }
    if (first.type === 'line' || first.type === 'bar') {
      const xData = (opt.xAxis?.data ?? []) as (string|number)[]
      const series = (opt.series ?? []).map((s: any) => ({ name: s.name, data: s.data ?? [] }))
      return { type: first.type as 'line'|'bar', xData, series, colors: opt.color ?? [] }
    }
    if (first.type === 'pie') {
      return { type: 'pie', data: (first.data ?? []).map((d: any) => ({ name: d.name, value: d.value })),
        colors: opt.color ?? [] }
    }
    return { type: 'unsupported' }
  } catch { return { type: 'unsupported' } }
}
