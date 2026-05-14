export type ConnectionDefaultsInput = {
  patch: { connectionId?: string | null; database?: string | null }
  current: { connectionId: string | null; database: string | null }
  connections: ReadonlyArray<{ id: string; databaseName: string | null }>
}

export type ConnectionDefaultsResult = {
  database: string | null
  appliedFallback: boolean
}

export function applyConnectionDefaultDatabase(
  input: ConnectionDefaultsInput,
): ConnectionDefaultsResult {
  const { patch, current, connections } = input

  if (patch.database !== undefined) {
    return { database: patch.database, appliedFallback: false }
  }

  const connectionChanged =
    patch.connectionId !== undefined
    && patch.connectionId !== null
    && patch.connectionId !== current.connectionId

  if (!connectionChanged) {
    return { database: current.database, appliedFallback: false }
  }

  const found = connections.find((connection) => connection.id === patch.connectionId)
  if (!found) {
    return { database: current.database, appliedFallback: false }
  }
  if (found.databaseName !== null && found.databaseName !== undefined) {
    return { database: found.databaseName, appliedFallback: true }
  }
  return { database: null, appliedFallback: false }
}
