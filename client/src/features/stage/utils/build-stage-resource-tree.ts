import type { Connection } from '@/services/api/connection'
import type { SessionDataContext } from '@/services/api/session-data-context'

export type StageResourceTool = 'sql' | 'er'

export type StageResourceToolActionNode = {
  kind: 'tool_action'
  id: string
  tool: StageResourceTool
  enabled: boolean
  comingSoon: boolean
}

export type StageResourceSchemaNode = {
  kind: 'schema'
  id: string
  connectionId: string
  label: string
  database: string
  schema: string
  current: boolean
  selected: boolean
  expanded: boolean
  actions: StageResourceToolActionNode[]
}

export type StageResourceDatabaseNode = {
  kind: 'database'
  id: string
  connectionId: string
  label: string
  database: string
  current: boolean
  selected: boolean
  expanded: boolean
  children: StageResourceSchemaNode[]
  actions: StageResourceToolActionNode[]
}

export type StageResourceConnectionNode = {
  kind: 'connection'
  id: string
  connectionId: string
  label: string
  current: boolean
  selected: boolean
  expanded: boolean
  children: StageResourceDatabaseNode[]
}

export type StageResourceTree = {
  connections: StageResourceConnectionNode[]
}

function buildToolActions(baseId: string): StageResourceToolActionNode[] {
  return [
    {
      kind: 'tool_action',
      id: `${baseId}:sql`,
      tool: 'sql',
      enabled: true,
      comingSoon: false,
    },
    {
      kind: 'tool_action',
      id: `${baseId}:er`,
      tool: 'er',
      enabled: false,
      comingSoon: true,
    },
  ]
}

function isCurrentConnection(context: SessionDataContext | null, connectionId: string) {
  return context?.connectionId === connectionId
}

function isCurrentDatabase(context: SessionDataContext | null, connectionId: string, database: string) {
  return context?.connectionId === connectionId && context.database === database
}

function isCurrentSchema(
  context: SessionDataContext | null,
  connectionId: string,
  database: string,
  schema: string,
) {
  return context?.connectionId === connectionId && context.database === database && context.schema === schema
}

function isSelectedConnection(context: SessionDataContext | null, connectionId: string) {
  return context?.connectionId === connectionId && context.selectedLevel === 'connection'
}

function isSelectedDatabase(context: SessionDataContext | null, connectionId: string, database: string) {
  return context?.connectionId === connectionId && context.database === database && context.selectedLevel === 'database'
}

function isSelectedSchema(
  context: SessionDataContext | null,
  connectionId: string,
  database: string,
  schema: string,
) {
  return context?.connectionId === connectionId
    && context.database === database
    && context.schema === schema
    && context.selectedLevel === 'schema'
}

function isExpanded(id: string, expandedNodeIds: string[]) {
  return expandedNodeIds.includes(id)
}

export function buildStageResourceTree(
  connections: Connection[],
  currentContext: SessionDataContext | null,
  expandedNodeIds: string[],
): StageResourceTree {
  return {
    connections: connections.map((connection) => {
      const connectionId = connection.id
      const connectionNodeId = `connection:${connectionId}`
      const contextDatabase = isCurrentConnection(currentContext, connectionId)
        ? currentContext?.database ?? null
        : null
      const connectionDatabase = contextDatabase ?? connection.databaseName ?? null
      const connectionChildren: StageResourceDatabaseNode[] = connectionDatabase
        ? [
            (() => {
              const databaseId = `database:${connectionId}:${connectionDatabase}`
              const databaseCurrent = isCurrentDatabase(currentContext, connectionId, connectionDatabase)
              const databaseSelected = isSelectedDatabase(currentContext, connectionId, connectionDatabase)
              const schemaValue = databaseCurrent ? currentContext?.schema ?? null : null
              const schemaChildren: StageResourceSchemaNode[] = schemaValue
                ? [
                    {
                      kind: 'schema',
                      id: `schema:${connectionId}:${connectionDatabase}:${schemaValue}`,
                      connectionId,
                      label: schemaValue,
                      database: connectionDatabase,
                      schema: schemaValue,
                      current: isCurrentSchema(currentContext, connectionId, connectionDatabase, schemaValue),
                      selected: isSelectedSchema(currentContext, connectionId, connectionDatabase, schemaValue),
                      expanded: isExpanded(`schema:${connectionId}:${connectionDatabase}:${schemaValue}`, expandedNodeIds),
                      actions: buildToolActions(`schema:${connectionId}:${connectionDatabase}:${schemaValue}`),
                    },
                  ]
                : []

              return {
                kind: 'database',
                id: databaseId,
                connectionId,
                label: connectionDatabase,
                database: connectionDatabase,
                current: databaseCurrent,
                selected: databaseSelected,
                expanded: isExpanded(databaseId, expandedNodeIds),
                children: schemaChildren,
                actions: schemaChildren.length > 0 ? [] : buildToolActions(databaseId),
              }
            })(),
          ]
        : []

      return {
        kind: 'connection',
        id: connectionNodeId,
        connectionId,
        label: connection.name,
        current: isCurrentConnection(currentContext, connectionId),
        selected: isSelectedConnection(currentContext, connectionId),
        expanded: isExpanded(connectionNodeId, expandedNodeIds),
        children: connectionChildren,
      }
    }),
  }
}
