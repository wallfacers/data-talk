import { ToolRegistry } from '../tool-registry'
import { ExecuteSql } from './execute-sql'
import { PreviewSql } from './preview-sql'
import { DescribeTable, ListTables, ShowSchema } from './metadata-renderers'
import { ArtifactCreated } from './artifact-created'
import { Question } from './question'
import { ReadFile } from './read-file'

let registered = false

export function registerBuiltInRenderers() {
  if (registered) return
  registered = true
  ToolRegistry.register('execute_sql', ExecuteSql)
  ToolRegistry.register('preview_sql', PreviewSql)
  ToolRegistry.register('describe_table', DescribeTable)
  ToolRegistry.register('list_tables', ListTables)
  ToolRegistry.register('show_schema', ShowSchema)
  ToolRegistry.register('artifact_created', ArtifactCreated)
  ToolRegistry.register('question', Question)
  ToolRegistry.register('read', ReadFile)
}
