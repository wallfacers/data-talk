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
      maximizedBySession: new Map(),
    })
    useOntologyStore.setState({ artifactsBySession: new Map() })
    useTimelineStore.setState({
      orderBySession: new Map(),
      activeBySession: new Map(),
      manualBySession: new Map(),
    })
  })

  it('渲染默认标题 Stage + 关闭 / 最大化 按钮', () => {
    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)
    expect(screen.getByLabelText('关闭')).toBeTruthy()
    expect(screen.getByLabelText('最大化')).toBeTruthy()
    expect(screen.getByText('Stage')).toBeTruthy()
  })

  it('点关闭触发 closeStage(sessionId)', () => {
    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)
    fireEvent.click(screen.getByLabelText('关闭'))
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('点最大化切换 maximizedBySession', () => {
    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)
    fireEvent.click(screen.getByLabelText('最大化'))
    expect(useStageStore.getState().maximizedBySession.get('s1')).toBe(true)
    fireEvent.click(screen.getByLabelText('还原'))
    expect(useStageStore.getState().maximizedBySession.get('s1')).toBe(false)
  })

  it('标题随 active artifact 变化', () => {
    useOntologyStore.getState().upsertArtifact('s1', { id: 'a1', version: 2, kind: 'chart' })
    useTimelineStore.setState({
      orderBySession: new Map([['s1', ['a1']]]),
      activeBySession: new Map([['s1', 'a1']]),
      manualBySession: new Map(),
    })
    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)
    expect(screen.getByText(/Stage · 图 v2/)).toBeTruthy()
  })

  it('children 渲染在 body slot', () => {
    render(<StageWindow sessionId="s1"><div data-testid="child">CHILD</div></StageWindow>)
    expect(screen.getByTestId('child')).toBeTruthy()
  })

  it('renders tab bar when store has tabs for session', () => {
    useStageStore.setState({
      tabsBySession: new Map([['s-1', [
        { tabId: 'q1', type: 'query_editor', title: 'SQL', scope: 'session' as const,
          originSessionId: 's-1', createdAt: 0, payload: {} },
      ]]]),
      activeTabIdBySession: new Map([['s-1', 'q1']]),
    })
    render(<StageWindow sessionId="s-1"><div>ai content</div></StageWindow>)
    expect(screen.getByText('SQL')).toBeTruthy()
  })

  it('shows children when store has no tabs', () => {
    useStageStore.setState({
      tabsBySession: new Map(),
      activeTabIdBySession: new Map(),
    })
    render(<StageWindow sessionId="s-1"><div>ai content</div></StageWindow>)
    expect(screen.getByText('ai content')).toBeTruthy()
  })
})
