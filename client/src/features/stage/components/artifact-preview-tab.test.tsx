import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { StageTab } from '@/stores/stage-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { ArtifactPreviewTab } from './artifact-preview-tab'
import { coordinator } from '@/features/stage/persistence/stage-persistence-bootstrap'

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: { ensureHydrated: vi.fn().mockResolvedValue(undefined) },
}))

vi.mock('@/features/ontology/components/artifact-dispatcher', () => ({
  ArtifactDispatcher: ({ artifact }: { artifact: { id: string } }) => (
    <div data-testid="artifact-dispatcher">{artifact.id}</div>
  ),
}))

const baseTab: StageTab = {
  tabId: 'artifact-tab-1',
  type: 'artifact_preview',
  title: 'Revenue Chart',
  originSessionId: 'sess-1',
  payload: {
    artifactId: 'art-1',
    sessionId: 'sess-1',
  },
  createdAt: 0,
}

describe('ArtifactPreviewTab', () => {
  beforeEach(() => {
    useOntologyStore.setState({ artifactsBySession: new Map() } as never)
    vi.mocked(coordinator.ensureHydrated).mockClear()
  })

  it('renders the artifact when payload and ontology data are available', () => {
    useOntologyStore.setState({
      artifactsBySession: new Map([
        ['sess-1', new Map([['art-1', { id: 'art-1', version: 1, kind: 'chart', payload: {} }]])],
      ]),
    } as never)

    render(<ArtifactPreviewTab tab={baseTab} />)

    expect(screen.getByTestId('artifact-dispatcher')).toHaveTextContent('art-1')
    expect(coordinator.ensureHydrated).not.toHaveBeenCalled()
  })

  it('requests hydration and shows a non-blank loading state when refresh restored only metadata', () => {
    render(<ArtifactPreviewTab tab={{ ...baseTab, payload: {}, payloadVersion: 3 }} />)

    expect(coordinator.ensureHydrated).toHaveBeenCalledWith('artifact-tab-1')
    expect(screen.getByText('AI 正在准备…')).toBeInTheDocument()
  })
})
