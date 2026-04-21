import { useMemo } from 'react'
import { ChevronDownIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'
import { normalizeError, showErrorToast } from '@/services/http-error'
import type { Connection } from '@/services/api/connection'
import { useSessionDataContext } from '@/features/session/hooks/use-session-data-context'
import {
  buildStageResourceTree,
  type StageResourceConnectionNode,
  type StageResourceDatabaseNode,
  type StageResourceSchemaNode,
  type StageResourceToolActionNode,
} from '@/features/stage/utils/build-stage-resource-tree'
import type { SidebarSelection } from '@/stores/stage-store'

type ResourceToolAction = {
  kind: 'resource_tool'
  tool: 'sql' | 'er'
  connectionId: string
  database: string
  schema?: string | null
}

type Props = {
  sessionId: string | null
  connections: Connection[]
  expandedNodeIds: string[]
  selection: SidebarSelection | null
  onSelectionChange: (selection: SidebarSelection | null) => void
  onExpandedChange: (nodeId: string, expanded: boolean) => void
  onToolAction: (action: ResourceToolAction) => void
}

function matchesSelection(
  node: StageResourceConnectionNode | StageResourceDatabaseNode | StageResourceSchemaNode,
  selection: SidebarSelection | null,
) {
  if (!selection) return false
  if (node.kind === 'connection') {
    return selection.kind === 'connection' && selection.connectionId === node.connectionId
  }
  if (node.kind === 'database') {
    return selection.kind === 'database' && selection.connectionId === node.connectionId && selection.database === node.database
  }
  return selection.kind === 'schema'
    && selection.connectionId === node.connectionId
    && selection.database === node.database
    && selection.schema === node.schema
}

function resourceSelectionForNode(
  node: StageResourceConnectionNode | StageResourceDatabaseNode | StageResourceSchemaNode,
): SidebarSelection {
  if (node.kind === 'connection') {
    return { kind: 'connection', connectionId: node.connectionId }
  }
  if (node.kind === 'database') {
    return { kind: 'database', connectionId: node.connectionId, database: node.database }
  }
  return { kind: 'schema', connectionId: node.connectionId, database: node.database, schema: node.schema }
}

function syncContextForNode(
  node: StageResourceConnectionNode | StageResourceDatabaseNode | StageResourceSchemaNode,
) {
  if (node.kind === 'connection') {
    return {
      connectionId: node.connectionId,
      database: null,
      schema: null,
      selectedLevel: 'connection' as const,
    }
  }
  if (node.kind === 'database') {
    return {
      connectionId: node.connectionId,
      database: node.database,
      schema: null,
      selectedLevel: 'database' as const,
    }
  }
  return {
    connectionId: node.connectionId,
    database: node.database,
    schema: node.schema,
    selectedLevel: 'schema' as const,
  }
}

function actionLabel(action: StageResourceToolActionNode, t: ReturnType<typeof useI18n>['t']) {
  return action.tool === 'sql' ? t('stage.toolRow.sql') : t('stage.toolRow.er')
}

function ResourceActions({
  node,
  onToolAction,
  setSessionDataContext,
  t,
}: {
  node: StageResourceDatabaseNode | StageResourceSchemaNode
  onToolAction: (action: ResourceToolAction) => void
  setSessionDataContext: (update: {
    connectionId: string | null
    database: string | null
    schema: string | null
    selectedLevel: 'connection' | 'database' | 'schema' | null
  }) => Promise<unknown>
  t: ReturnType<typeof useI18n>['t']
}) {
  if (!node.actions.length) return null

  return (
    <div className="mt-1 flex flex-wrap gap-1 pl-4">
      {node.actions.map((action) => {
        const label = actionLabel(action, t)
        const comingSoon = t('stage.toolRow.comingSoon')
        return (
          <Button
            key={action.id}
            type="button"
            size="xs"
            variant="ghost"
            disabled={!action.enabled}
            aria-label={action.comingSoon ? `${label} ${comingSoon}` : label}
            onClick={async () => {
              if (!action.enabled) return
              try {
                await setSessionDataContext({
                  connectionId: node.connectionId,
                  database: node.database,
                  schema: node.kind === 'schema' ? node.schema : null,
                  selectedLevel: node.kind === 'schema' ? 'schema' : 'database',
                })
                onToolAction({
                  kind: 'resource_tool',
                  tool: action.tool,
                  connectionId: node.connectionId,
                  database: node.database,
                  ...(node.kind === 'schema' ? { schema: node.schema } : {}),
                })
              } catch (error) {
                showErrorToast(normalizeError(error))
              }
            }}
            className={cn('h-6 justify-start gap-1 rounded-md px-2 text-xs', !action.enabled && 'text-muted-foreground')}
          >
            <span className="truncate">{label}</span>
            {action.comingSoon && (
              <Badge variant="outline" className="ml-1 h-4 px-1.5 text-[10px]">
                {comingSoon}
              </Badge>
            )}
          </Button>
        )
      })}
    </div>
  )
}

function ResourceNode({
  node,
  selection,
  onSelectionChange,
  onExpandedChange,
  onToolAction,
  setSessionDataContext,
  t,
}: {
  node: StageResourceConnectionNode | StageResourceDatabaseNode | StageResourceSchemaNode
  selection: SidebarSelection | null
  onSelectionChange: (selection: SidebarSelection | null) => void
  onExpandedChange: (nodeId: string, expanded: boolean) => void
  onToolAction: (action: ResourceToolAction) => void
  setSessionDataContext: (update: {
    connectionId: string | null
    database: string | null
    schema: string | null
    selectedLevel: 'connection' | 'database' | 'schema' | null
  }) => Promise<unknown>
  t: ReturnType<typeof useI18n>['t']
}) {
  const isSelected = matchesSelection(node, selection) || node.selected
  const hasChildren = node.kind !== 'schema' && node.children.length > 0

  return (
    <div className={cn('rounded-md', isSelected && 'bg-muted/60')}>
      <Button
        type="button"
        variant="ghost"
        size="xs"
        aria-label={node.label}
        aria-current={isSelected ? 'true' : undefined}
        onClick={async () => {
          onSelectionChange(resourceSelectionForNode(node))
          if (hasChildren) {
            onExpandedChange(node.id, !node.expanded)
          }
          try {
            await setSessionDataContext(syncContextForNode(node))
          } catch (error) {
            showErrorToast(normalizeError(error))
          }
        }}
        className="h-7 w-full justify-start gap-1.5 px-2 text-left font-normal"
      >
        {hasChildren ? (
          node.expanded ? <ChevronDownIcon className="size-3.5 shrink-0" /> : <ChevronRightIcon className="size-3.5 shrink-0" />
        ) : (
          <span className="size-3.5 shrink-0" />
        )}
        <span className="truncate">{node.label}</span>
      </Button>

      {node.kind !== 'schema' && node.expanded && node.children.length > 0 && (
        <div className="ml-4 border-l border-border/60 pl-2">
          {node.children.map((child) => (
            <ResourceNode
              key={child.id}
              node={child}
              selection={selection}
              onSelectionChange={onSelectionChange}
              onExpandedChange={onExpandedChange}
              onToolAction={onToolAction}
              setSessionDataContext={setSessionDataContext}
              t={t}
            />
          ))}
        </div>
      )}

      {node.kind === 'connection' && node.expanded && node.children.length === 0 && (
        <div className="ml-6 px-2 py-1 text-xs text-muted-foreground">
          {t('stage.resourceBrowser.emptyChildren')}
        </div>
      )}

      {node.kind !== 'connection' && (
        <ResourceActions
          node={node}
          onToolAction={onToolAction}
          setSessionDataContext={setSessionDataContext}
          t={t}
        />
      )}
    </div>
  )
}

export function StageResourceBrowser({
  sessionId,
  connections,
  expandedNodeIds,
  selection,
  onSelectionChange,
  onExpandedChange,
  onToolAction,
}: Props) {
  const { t } = useI18n()
  const { context, setSessionDataContext } = useSessionDataContext(sessionId)
  const tree = useMemo(() => buildStageResourceTree(connections, context, expandedNodeIds), [connections, context, expandedNodeIds])
  const hasResourceDescendants = tree.connections.some((node) => node.children.length > 0)

  if (!tree.connections.length) {
    return <div className="rounded-md border border-dashed border-border/60 px-3 py-4 text-sm text-muted-foreground">{t('stage.resourceBrowser.empty')}</div>
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 px-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {t('stage.resourceBrowser.title')}
      </div>
      {!hasResourceDescendants && (
        <div className="rounded-md border border-dashed border-border/60 px-3 py-3 text-xs text-muted-foreground">
          {t('stage.resourceBrowser.emptyChildren')}
        </div>
      )}
      <div role="tree" className="space-y-1">
        {tree.connections.map((node) => (
          <ResourceNode
            key={node.id}
            node={node}
            selection={selection}
            onSelectionChange={onSelectionChange}
            onExpandedChange={onExpandedChange}
            onToolAction={onToolAction}
            setSessionDataContext={setSessionDataContext}
            t={t}
          />
        ))}
      </div>
    </div>
  )
}
