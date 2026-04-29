import { ToolRegistry } from '../tool-registry'
import { ExecuteSql } from './execute-sql'
import { ShowSchema } from './metadata-renderers'
import { ArtifactCreated } from './artifact-created'
import './diagnostics-card'

let registered = false

export function registerBuiltInRenderers() {
  if (registered) return
  registered = true
  ToolRegistry.register('datatalk_execute_sql', ExecuteSql)
  ToolRegistry.register('datatalk_read_schema', ShowSchema)
  ToolRegistry.register('datatalk_render_chart', ArtifactCreated)
}
