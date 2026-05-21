import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useFileArtifactsStore } from '../file-artifacts-store'
import type { FileArtifact } from '@/services/api/file-artifacts'

const baseArtifact: FileArtifact = {
  id: 'fa_1',
  scope: 'session',
  status: 'temporary',
  kind: 'dataset',
  sessionId: 'ses_a',
  connectionId: null,
  filename: 'sample.csv',
  physicalPath: '/home/u/.data-talk/opencode/sessions/ses_a/sample.csv',
  sizeBytes: 2_100_000,
  mimeType: 'text/csv',
  title: null,
  summary: null,
  createdAt: '2026-04-29T00:00:00Z',
  updatedAt: '2026-04-29T00:00:00Z',
  archivedAt: null,
  metadata: {},
}

describe('useFileArtifactsStore', () => {
  beforeEach(() => {
    useFileArtifactsStore.setState({
      bySessionId: {},
      byConnectionId: {},
      loading: false,
      error: null,
    })
  })

  it('selectSessionFiles groups by status (temporary vs candidate)', () => {
    useFileArtifactsStore.setState({
      bySessionId: {
        ses_a: [
          baseArtifact,
          { ...baseArtifact, id: 'fa_2', status: 'candidate', filename: 'orders-er.md', kind: 'er_diagram' },
        ],
      },
    })
    const grouped = useFileArtifactsStore.getState().selectSessionFiles('ses_a')
    expect(grouped.temporary.map((f) => f.id)).toEqual(['fa_1'])
    expect(grouped.candidate.map((f) => f.id)).toEqual(['fa_2'])
  })

  it('selectConnectionFiles groups by kind', () => {
    useFileArtifactsStore.setState({
      byConnectionId: {
        conn_x: [
          { ...baseArtifact, id: 'fa_3', status: 'archived', kind: 'er_diagram', scope: 'workspace', connectionId: 'conn_x' },
          { ...baseArtifact, id: 'fa_4', status: 'archived', kind: 'sql_script', scope: 'workspace', connectionId: 'conn_x' },
        ],
      },
    })
    const grouped = useFileArtifactsStore.getState().selectConnectionFiles('conn_x')
    expect(grouped.er_diagram).toHaveLength(1)
    expect(grouped.sql_script).toHaveLength(1)
    expect(grouped.report).toHaveLength(0)
  })

  it('applyDtEvent file_artifact.detected appends a temporary row to bySessionId', () => {
    useFileArtifactsStore.getState().applyDtEvent({
      type: 'file_artifact.detected',
      data: {
        fileArtifactId: 'fa_new',
        sessionId: 'ses_a',
        filename: 'sample.csv',
        kind: 'dataset',
        status: 'temporary',
        sizeBytes: 2_100_000,
      },
    })
    expect(useFileArtifactsStore.getState().bySessionId.ses_a).toHaveLength(1)
    expect(useFileArtifactsStore.getState().bySessionId.ses_a[0].status).toBe('temporary')
  })

  it('applyDtEvent file_artifact.archive_requested promotes status temporary -> candidate', () => {
    useFileArtifactsStore.setState({ bySessionId: { ses_a: [baseArtifact] } })
    useFileArtifactsStore.getState().applyDtEvent({
      type: 'file_artifact.archive_requested',
      data: {
        fileArtifactId: 'fa_1',
        sessionId: 'ses_a',
        kind: 'dataset',
        title: 'Sample',
        summary: 'CSV sample',
      },
    })
    expect(useFileArtifactsStore.getState().bySessionId.ses_a[0].status).toBe('candidate')
    expect(useFileArtifactsStore.getState().bySessionId.ses_a[0].title).toBe('Sample')
  })

  it('applyDtEvent file_artifact.archived moves the row to byConnectionId and detaches from session', () => {
    useFileArtifactsStore.setState({
      bySessionId: { ses_a: [{ ...baseArtifact, status: 'candidate' }] },
    })
    useFileArtifactsStore.getState().applyDtEvent({
      type: 'file_artifact.archived',
      data: {
        fileArtifactId: 'fa_1',
        sessionId: 'ses_a',
        connectionId: 'conn_x',
        filename: 'sample.csv',
        physicalPath: '/home/u/.data-talk/workspaces/conn_x/sample.csv',
      },
    })
    const state = useFileArtifactsStore.getState()
    expect(state.bySessionId.ses_a).toEqual([])
    expect(state.byConnectionId.conn_x).toHaveLength(1)
    expect(state.byConnectionId.conn_x[0].status).toBe('archived')
    expect(state.byConnectionId.conn_x[0].scope).toBe('workspace')
  })

  it('applyDtEvent file_artifact.discarded removes the row from any list', () => {
    useFileArtifactsStore.setState({
      bySessionId: { ses_a: [baseArtifact] },
    })
    useFileArtifactsStore.getState().applyDtEvent({
      type: 'file_artifact.discarded',
      data: { fileArtifactId: 'fa_1', reason: 'user_action' },
    })
    expect(useFileArtifactsStore.getState().bySessionId.ses_a).toEqual([])
  })

  it('fetchForSession populates bySessionId via API mock', async () => {
    const api = await import('@/services/api/file-artifacts')
    const spy = vi.spyOn(api, 'listSessionFiles').mockResolvedValue([baseArtifact])
    await useFileArtifactsStore.getState().fetchForSession('ses_a')
    expect(spy).toHaveBeenCalledWith('ses_a')
    expect(useFileArtifactsStore.getState().bySessionId.ses_a).toHaveLength(1)
    expect(useFileArtifactsStore.getState().loading).toBe(false)
    spy.mockRestore()
  })

  it('fetchForSession sets error message on rejection', async () => {
    const api = await import('@/services/api/file-artifacts')
    const spy = vi.spyOn(api, 'listSessionFiles').mockRejectedValue(new Error('boom'))
    await useFileArtifactsStore.getState().fetchForSession('ses_a')
    expect(useFileArtifactsStore.getState().error).toBe('boom')
    expect(useFileArtifactsStore.getState().loading).toBe(false)
    spy.mockRestore()
  })
})
