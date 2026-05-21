import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { GenericTool } from '../renderers/generic-tool'
import type { ActionDescriptor } from '@/features/actions/registry'
import type { ToolPart } from '@/services/channel/types'

const descriptor: ActionDescriptor = {
  id: 'datatalk.ui.find',
  executor: 'SERVER',
  description: 'List UI objects',
  inputSchema: {},
  outputSchema: {},
  produces: [],
  sideEffects: [],
  requiresConnection: false,
  timeoutMs: 30000,
  category: 'misc',
}

function buildPart(input: Record<string, unknown>): ToolPart {
  return {
    id: 'part-1',
    type: 'tool',
    sessionID: 'sess-1',
    messageID: 'msg-1',
    tool: 'datatalk_ui_find',
    state: {
      status: 'completed',
      input,
      output: 'ok',
      metadata: {},
    },
  }
}

describe('GenericTool', () => {
  it('hides system args from the trigger and reveals full values on expand', () => {
    render(
      <GenericTool
        part={buildPart({
          __dtOpenCodeSessionId: 'ses_241b8620bffeURHQFdER7THDyw',
          __dtCallId: 'call_83a2bab1d31b4b5fbf4d6556',
          __dtBridgeNonce: '89a6862c-4940-4539-ab6b-942e5d3d01fc',
        })}
        descriptor={descriptor}
      />,
    )

    const trigger = screen.getByRole('button')

    expect(trigger).not.toHaveTextContent('dtOpenCodeSessionId')
    expect(trigger).not.toHaveTextContent('dtCallId')
    expect(trigger).not.toHaveTextContent('dtBridgeNonce')
    expect(trigger).not.toHaveTextContent('ses_241b8620bffeURHQFdER7THDyw')
    expect(trigger).not.toHaveTextContent('call_83a2bab1d31b4b5fbf4d6556')
    expect(trigger).not.toHaveTextContent('89a6862c-4940-4539-ab6b-942e5d3d01fc')

    fireEvent.click(trigger)

    expect(screen.getByText(/__dtOpenCodeSessionId=ses_241b8620bffeURHQFdER7THDyw/)).toBeTruthy()
    expect(screen.getByText(/__dtCallId=call_83a2bab1d31b4b5fbf4d6556/)).toBeTruthy()
    expect(screen.getByText(/__dtBridgeNonce=89a6862c-4940-4539-ab6b-942e5d3d01fc/)).toBeTruthy()
  })

  it('moves readable args into details and keeps only the tool name in the trigger', () => {
    render(
      <GenericTool
        part={buildPart({
          object: 'workspace',
          action: 'open',
          limit: 10,
        })}
        descriptor={descriptor}
      />,
    )

    const trigger = screen.getByRole('button')

    expect(trigger).not.toHaveTextContent('object=workspace')
    expect(trigger).not.toHaveTextContent('action=open')
    expect(trigger).not.toHaveTextContent('limit=10')

    fireEvent.click(trigger)

    expect(screen.getByText(/object=workspace/)).toBeTruthy()
    expect(screen.getByText(/action=open/)).toBeTruthy()
    expect(screen.getByText(/limit=10/)).toBeTruthy()
  })
})
