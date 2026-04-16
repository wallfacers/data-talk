import type { ComponentType } from 'react'
import type { Part } from '@/services/channel/types'
import type { Artifact } from '@/services/channel/event-reducer'

export type LeftCardProps = { part: Part; descriptor: ActionDescriptor }
export type RightArtifactProps = { artifact: Artifact }

export type ActionDescriptor = {
  id: string
  executor: 'OPENCODE' | 'SERVER' | 'CLIENT'
  description: string
  inputSchema: unknown
  outputSchema: unknown
  produces: string[]
  sideEffects: string[]
  requiresConnection: boolean
  timeoutMs: number
}

export type ActionRenderers = {
  leftCard?: ComponentType<LeftCardProps>
  rightArtifact?: ComponentType<RightArtifactProps>
}

export type ClientActionHandler = (input: unknown, ctx: { sessionId: string }) => Promise<unknown>

const renderers = new Map<string, ActionRenderers>()
const clientHandlers = new Map<string, ClientActionHandler>()

export function registerAction(id: string, r: ActionRenderers) {
  renderers.set(id, r)
}
export function registerClientHandler(id: string, h: ClientActionHandler) {
  clientHandlers.set(id, h)
}
export function getRenderers(id: string): ActionRenderers | undefined { return renderers.get(id) }
export function getClientHandler(id: string): ClientActionHandler | undefined { return clientHandlers.get(id) }
