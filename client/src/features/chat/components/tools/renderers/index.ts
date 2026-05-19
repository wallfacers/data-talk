import { ToolRegistry } from '../tool-registry'
import { ExecuteSql } from './execute-sql'
import { ShowSchema } from './metadata-renderers'
import { ArtifactCreated } from './artifact-created'
import { DatatalkArchiveArtifact } from './datatalk-archive-artifact'
import { ExportData } from './export-data'
import './diagnostics-card'

let registered = false

export function registerBuiltInRenderers() {
  if (registered) return
  registered = true
  ToolRegistry.register('datatalk_execute_sql', ExecuteSql)
  ToolRegistry.register('datatalk_read_schema', ShowSchema)
  ToolRegistry.register('datatalk_render_chart', ArtifactCreated)
  ToolRegistry.register('datatalk_archive_artifact', DatatalkArchiveArtifact)
  ToolRegistry.register('datatalk.export_data', ExportData)
}
