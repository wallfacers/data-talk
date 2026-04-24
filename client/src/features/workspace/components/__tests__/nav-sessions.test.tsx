import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { NavSessions } from '../nav-sessions'
import { SidebarProvider } from '@/components/ui/sidebar'
import { useSessionStore } from '@/stores/session-store'
import * as api from '@/services/api/session'

vi.mock('@/features/connection/store', () => ({
  useConnectionStore: (sel: any) => sel({ activeConnectionId: 'c1' }),
}))

function renderWithCache(initial: api.Session[]) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  qc.setQueryData(['sessions', null], initial)
  qc.setQueryData(
    ['sessions', 'c1'],
    initial.filter((session) => session.connectionId === 'c1'),
  )
  render(
    <QueryClientProvider client={qc}>
      <SidebarProvider>
        <NavSessions />
      </SidebarProvider>
    </QueryClientProvider>,
  )
  return qc
}

const mkSession = (over: Partial<api.Session>): api.Session => ({
  id: 'x', connectionId: 'c1', title: 't', hasEverSent: true,
  createdAt: Date.now(), updatedAt: Date.now(),
  titleLocked: false, reusedEmpty: false,
  ...over,
})

describe('NavSessions — 删除当前活跃会话', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    useSessionStore.setState({
      activeSessionId: 'cur',
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
    })
  })

  it('删除当前会话 → 复用缓存中的空白会话', async () => {
    const delSpy = vi.spyOn(api, 'deleteSession').mockResolvedValue(undefined)
    const createSpy = vi.spyOn(api, 'createSession')
    renderWithCache([
      mkSession({ id: 'cur', title: '当前' }),
      mkSession({ id: 'empty', title: '新会话', hasEverSent: false }),
    ])

    fireEvent.click(screen.getAllByRole('button', { name: '更多' })[0])
    fireEvent.click(await screen.findByText('删除'))
    fireEvent.click(screen.getByText('删除', { selector: 'button' }))

    await waitFor(() => expect(delSpy).toHaveBeenCalledWith('cur'))
    await waitFor(() =>
      expect(useSessionStore.getState().activeSessionId).toBe('empty'),
    )
    expect(createSpy).not.toHaveBeenCalled()
  })

  it('删除当前会话且无空白 → 触发 createSession', async () => {
    const delSpy = vi.spyOn(api, 'deleteSession').mockResolvedValue(undefined)
    const createSpy = vi.spyOn(api, 'createSession').mockResolvedValue(
      mkSession({ id: 'fresh', hasEverSent: false }),
    )
    renderWithCache([mkSession({ id: 'cur', title: '当前' })])

    fireEvent.click(screen.getByRole('button', { name: '更多' }))
    fireEvent.click(await screen.findByText('删除'))
    fireEvent.click(screen.getByText('删除', { selector: 'button' }))

    await waitFor(() => expect(delSpy).toHaveBeenCalledWith('cur'))
    await waitFor(() => expect(createSpy).toHaveBeenCalledTimes(1))
    await waitFor(() =>
      expect(useSessionStore.getState().activeSessionId).toBe('fresh'),
    )
  })

  it('删除非当前会话 → 不动 activeSessionId、不创建空白', async () => {
    const delSpy = vi.spyOn(api, 'deleteSession').mockResolvedValue(undefined)
    const createSpy = vi.spyOn(api, 'createSession')
    renderWithCache([
      mkSession({ id: 'cur', title: '当前' }),
      mkSession({ id: 'other', title: '别的' }),
    ])

    const moreBtns = screen.getAllByRole('button', { name: '更多' })
    fireEvent.click(moreBtns[1])
    fireEvent.click(await screen.findByText('删除'))
    fireEvent.click(screen.getByText('删除', { selector: 'button' }))

    await waitFor(() => expect(delSpy).toHaveBeenCalledWith('other'))
    expect(useSessionStore.getState().activeSessionId).toBe('cur')
    expect(createSpy).not.toHaveBeenCalled()
  })

  it('空白会话不显示更多按钮', () => {
    renderWithCache([
      mkSession({ id: 'empty', title: '新会话', hasEverSent: false }),
    ])

    expect(screen.queryByRole('button', { name: '更多' })).not.toBeInTheDocument()
    expect(screen.getByText('新会话')).toBeInTheDocument()
  })

  it('普通会话仍显示更多按钮和删除入口', async () => {
    renderWithCache([
      mkSession({ id: 'normal', title: '普通会话', hasEverSent: true }),
      mkSession({ id: 'empty', title: '新会话', hasEverSent: false }),
    ])

    expect(screen.getAllByRole('button', { name: '更多' })).toHaveLength(1)

    fireEvent.click(screen.getByRole('button', { name: '更多' }))

    expect(await screen.findByText('删除')).toBeInTheDocument()
  })

  it('切换活动数据源后，侧栏仍显示全量历史会话', () => {
    renderWithCache([
      mkSession({ id: 's-null', connectionId: null, title: '无绑定历史' }),
      mkSession({ id: 's-c2', connectionId: 'c2', title: '另一个库的历史' }),
    ])

    expect(screen.getByText('无绑定历史')).toBeInTheDocument()
    expect(screen.getByText('另一个库的历史')).toBeInTheDocument()
  })

  it('隐藏 stage-ai 内部会话，不污染常规会话列表', () => {
    renderWithCache([
      mkSession({ id: 'visible', title: '普通会话' }),
      mkSession({ id: 'internal', title: '_stage-ai_Orders SQL' }),
    ])

    expect(screen.getByText('普通会话')).toBeInTheDocument()
    expect(screen.queryByText('_stage-ai_Orders SQL')).not.toBeInTheDocument()
  })

  it('真实标题返回前不展示 OpenCode 时间戳临时标题', () => {
    renderWithCache([
      mkSession({ id: 'pending-title', title: 'NewSession · 2026-04-24 10:12', hasEverSent: true }),
    ])

    expect(screen.getByText('新会话')).toBeInTheDocument()
    expect(screen.queryByText('NewSession · 2026-04-24 10:12')).not.toBeInTheDocument()
  })
})
