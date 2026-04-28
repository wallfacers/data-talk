import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { registerBuiltInRenderers } from '../renderers'
import { ReadFile } from '../renderers/read-file'
import { ToolRegistry } from '../tool-registry'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { useUISettingsStore } from '@/stores/ui-settings-store'

const openStageSpy = vi.hoisted(() => vi.fn())

function resetStores() {
  useSessionStore.setState({
    activeSessionId: null,
  } as any)
  useUISettingsStore.setState({
    language: 'en-US',
  } as any)

  useStageStore.setState({
    openBySession: new Map(),
    autoOpenedSessions: new Set(),
    maximizedBySession: new Map(),
    revealOrigin: null,
    sidebarCollapsedBySession: new Map(),
    sidebarSelectionBySession: new Map(),
    resourceTreeExpandedBySession: new Map(),
    activeRailPanelBySession: new Map(),
    workspaceTabs: [],
    tabsBySession: new Map(),
    activeWorkspaceTabId: null,
    activeTabIdBySession: new Map(),
    openStage: openStageSpy,
  } as any)
}

function buildPart(overrides: Partial<Parameters<typeof ReadFile>[0]['part']> = {}) {
  return {
    id: 'part-1',
    type: 'tool',
    sessionID: 'sess-1',
    messageID: 'msg-1',
    tool: 'read',
    state: {
      status: 'completed',
      input: {},
      output: '<path>/tmp/readme.md</path><type>file</type><content># Hello</content>',
      metadata: {},
    },
    ...overrides,
  } as Parameters<typeof ReadFile>[0]['part']
}

describe('read-file renderer', () => {
  beforeEach(() => {
    openStageSpy.mockReset()
    resetStores()
  })

  it('does not register the legacy read renderer in the MCP renderer subset', () => {
    registerBuiltInRenderers()

    expect(ToolRegistry.get('read')).toBeUndefined()
  })

  it('shows a stage button for completed parseable file output and opens a file preview tab', async () => {
    render(
      <ReadFile
        part={buildPart()}
        descriptor={{ id: 'read', executor: 'SERVER', description: 'read', inputSchema: {}, outputSchema: {}, produces: [], sideEffects: [], requiresConnection: false, timeoutMs: 30000 } as any}
      />,
    )

    const button = screen.getByRole('button', { name: 'Open Workbench' })
    fireEvent.click(button)
    fireEvent.click(button)

    expect(openStageSpy).toHaveBeenCalledWith()
    expect(openStageSpy).toHaveBeenCalledTimes(2)

    const tabs = useStageStore.getState().tabs
    expect(tabs).toHaveLength(1)
    expect(tabs[0]).toEqual(expect.objectContaining({
      type: 'file_preview',
      scope: 'session',
      originSessionId: 'sess-1',
      title: 'readme.md',
      payload: expect.objectContaining({
        sourceKey: 'msg-1:part-1',
        filePath: '/tmp/readme.md',
        filename: 'readme.md',
        fileType: 'file',
        content: '# Hello',
        truncated: false,
        language: 'markdown',
      }),
    }))
  })

  it('uses active session id when opening workbench from header action', () => {
    useSessionStore.setState({ activeSessionId: 'sess-2' } as any)

    render(
      <ReadFile
        part={buildPart({ sessionID: 'sess-1' })}
        descriptor={{ id: 'read', executor: 'SERVER', description: 'read', inputSchema: {}, outputSchema: {}, produces: [], sideEffects: [], requiresConnection: false, timeoutMs: 30000 } as any}
      />,
    )

    const button = screen.getByRole('button', { name: 'Open Workbench' })
    fireEvent.click(button)

    expect(openStageSpy).toHaveBeenCalledWith()
    const tabs = useStageStore.getState().tabs
    expect(tabs).toHaveLength(1)
  })

  it('falls back to GenericTool output when parse fails', () => {
    render(
      <ReadFile
        part={buildPart({
          state: {
            status: 'completed',
            input: {},
            output: 'plain text output',
            metadata: {},
          },
        })}
        descriptor={{ id: 'read', executor: 'SERVER', description: 'read', inputSchema: {}, outputSchema: {}, produces: [], sideEffects: [], requiresConnection: false, timeoutMs: 30000 } as any}
      />,
    )

    expect(screen.queryByRole('button', { name: 'Open Workbench' })).toBeNull()
    expect(screen.getByText('plain text output')).toBeTruthy()
  })

  it('falls back to GenericTool output when there is no usable session id', () => {
    render(
      <ReadFile
        part={buildPart({
          sessionID: '   ',
          state: {
            status: 'completed',
            input: {},
            output: '<path>/tmp/readme.md</path><type>file</type><content># Hello</content>',
            metadata: {},
          },
        })}
        descriptor={{ id: 'read', executor: 'SERVER', description: 'read', inputSchema: {}, outputSchema: {}, produces: [], sideEffects: [], requiresConnection: false, timeoutMs: 30000 } as any}
      />,
    )

    expect(screen.queryByRole('button', { name: 'Open Workbench' })).toBeNull()
    expect(screen.getByText('<path>/tmp/readme.md</path><type>file</type><content># Hello</content>')).toBeTruthy()
  })

  it.each([
    {
      label: 'pending status',
      part: buildPart({
          state: {
            status: 'pending',
            input: {},
            output: '<path>/tmp/readme.md</path><type>file</type><content># Hello</content>',
            metadata: {},
          },
        }),
    },
    {
      label: 'non-file output',
      part: buildPart({
        state: {
          status: 'completed',
          input: {},
          output: 'not tagged as a file',
          metadata: {},
        },
      }),
    },
  ])('does not show the button for $label', ({ part }) => {
    render(
      <ReadFile
        part={part}
        descriptor={{ id: 'read', executor: 'SERVER', description: 'read', inputSchema: {}, outputSchema: {}, produces: [], sideEffects: [], requiresConnection: false, timeoutMs: 30000 } as any}
      />,
    )

    expect(screen.queryByRole('button', { name: 'Open Workbench' })).toBeNull()
  })
})
