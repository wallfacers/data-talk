import { beforeEach, describe, expect, it, vi } from 'vitest'

describe('registerBuiltInRenderers', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  it('registers only the datatalk MCP renderer subset', async () => {
    const { registerBuiltInRenderers } = await import('../renderers')
    const { ToolRegistry } = await import('../tool-registry')
    const { ExecuteSql } = await import('../renderers/execute-sql')
    const { ShowSchema } = await import('../renderers/metadata-renderers')
    const { ArtifactCreated } = await import('../renderers/artifact-created')
    const { Question } = await import('../renderers/question')

    registerBuiltInRenderers()

    expect(ToolRegistry.get('datatalk_execute_sql')).toBe(ExecuteSql)
    expect(ToolRegistry.get('datatalk_read_schema')).toBe(ShowSchema)
    expect(ToolRegistry.get('datatalk_render_chart')).toBe(ArtifactCreated)
    expect(ToolRegistry.get('question')).toBe(Question)

    for (const legacyKey of ['execute_sql', 'show_schema', 'artifact_created', 'read']) {
      expect(ToolRegistry.get(legacyKey)).toBeUndefined()
    }
  })
})
