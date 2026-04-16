import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StageWindow } from './stage-window'
import { useStageStore } from '@/stores/stage-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'

describe('StageWindow', () => {
  beforeEach(() => {
    useStageStore.setState({
      openBySession: new Map([['s1', true]]),
      autoOpenedSessions: new Set(),
    })
    useOntologyStore.setState({ artifactsBySession: new Map() })
    useTimelineStore.setState({
      orderBySession: new Map(),
      activeBySession: new Map(),
      manualBySession: new Map(),
    })
  })

  it('渲染 close 按钮 + 默认标题 Stage', () => {
    render(
      <StageWindow sessionId="s1">
        <div>body</div>
      </StageWindow>,
    )
    expect(screen.getByLabelText('关闭 Stage')).toBeTruthy()
    expect(screen.getByText('Stage')).toBeTruthy()
  })

  it('点红圆点触发 closeStage', () => {
    render(
      <StageWindow sessionId="s1">
        <div>body</div>
      </StageWindow>,
    )
    fireEvent.click(screen.getByLabelText('关闭 Stage'))
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('标题随 active artifact 变化', () => {
    useOntologyStore.getState().upsertArtifact('s1', { id: 'a1', version: 2, kind: 'chart' })
    useTimelineStore.setState({
      orderBySession: new Map([['s1', ['a1']]]),
      activeBySession: new Map([['s1', 'a1']]),
      manualBySession: new Map(),
    })
    render(
      <StageWindow sessionId="s1">
        <div>body</div>
      </StageWindow>,
    )
    expect(screen.getByText(/Stage · 图 v2/)).toBeTruthy()
  })

  it('children 渲染在 body slot', () => {
    render(
      <StageWindow sessionId="s1">
        <div data-testid="child">CHILD</div>
      </StageWindow>,
    )
    expect(screen.getByTestId('child')).toBeTruthy()
  })
})
