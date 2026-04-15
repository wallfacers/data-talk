export type WorkspaceTabKind = 'query-result' | 'er-diagram' | 'sql-editor'

export type WorkspaceTab = {
  id: string
  title: string
  kind: WorkspaceTabKind
  payload?: unknown
}
