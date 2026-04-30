import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ChartRenderer } from '../chart-renderer'

// echarts-for-react triggers real ECharts which needs a full canvas runtime,
// so we stub it and assert on the props the wrapper passes through. The
// regression we are guarding against is the wrapper sliding back to a
// pixel-measured width that desyncs from the parent on resize and pushes
// pie centres off-axis. jsdom does not lay out, so this test cannot prove
// the pie ends up at the visual centre — it only locks the inline-style
// contract that lets echarts-for-react's own `autoResize` keep things
// centred. Real layout regressions need a Tauri / browser pass.
const reactEchartsCalls: Array<{ style: React.CSSProperties }> = []
vi.mock('echarts-for-react', () => ({
  default: vi.fn((props: { style: React.CSSProperties }) => {
    reactEchartsCalls.push({ style: props.style })
    return <div data-testid="react-echarts-mock" style={props.style} />
  }),
}))

const PIE_OPTION = {
  series: [{ type: 'pie', radius: '60%', data: [{ value: 1 }, { value: 2 }] }],
}

describe('ChartRenderer', () => {
  it('keeps the echarts wrapper at width:100% so the parent and canvas stay in sync', () => {
    reactEchartsCalls.length = 0

    render(<ChartRenderer option={PIE_OPTION} />)

    expect(screen.getByTestId('react-echarts-mock')).toBeInTheDocument()
    expect(reactEchartsCalls.length).toBeGreaterThan(0)
    const lastCall = reactEchartsCalls[reactEchartsCalls.length - 1]
    expect(lastCall.style.width).toBe('100%')
  })
})
