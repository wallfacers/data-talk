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

  // BUG-0010 v4: chat-bubble charts must reserve enough vertical room for
  // title + legend + plot area + axisLabel + axis.name. The previous default
  // (320) plus chart-theme's grid.bottom (80) still clipped the centred
  // X-axis name at the canvas bottom. v4 raises the default to ≥ 360 so
  // there's a proper margin below the inflated grid.
  it('uses a default height of at least 360px so chat-bubble charts can host an axis name without clipping', () => {
    reactEchartsCalls.length = 0

    render(<ChartRenderer option={PIE_OPTION} />)

    const lastCall = reactEchartsCalls[reactEchartsCalls.length - 1]
    expect(typeof lastCall.style.height).toBe('number')
    expect(lastCall.style.height as number).toBeGreaterThanOrEqual(360)
  })
})
