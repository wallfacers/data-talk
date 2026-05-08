import { format, type SqlLanguage } from 'sql-formatter'

export function resolveSqlFormatterLanguage(connectionKind: string | null | undefined): SqlLanguage {
  switch (connectionKind?.trim().toLowerCase()) {
    case 'postgres':
    case 'postgresql':
      return 'postgresql'
    case 'mysql':
    case 'mariadb':
    case 'apache_doris':
    case 'starrocks': // sql-formatter has no starrocks dialect; mysql is the closest approximation (day-1). OLAP-specific syntax (PROPERTIES, KEY clauses, DISTRIBUTED BY) may format suboptimally.
      return 'mysql'
    case 'oracle':
      return 'plsql'
    case 'sqlserver':
      return 'transactsql'
    case 'h2':
    case 'duckdb':
    case 'trino':
    case 'presto':
    case 'hive':
    default:
      return 'sql'
  }
}

export function formatSql(sql: string, connectionKind: string | null | undefined): string {
  if (!sql.trim()) return sql

  try {
    return format(sql, {
      language: resolveSqlFormatterLanguage(connectionKind),
      keywordCase: 'upper',
      tabWidth: 2,
      logicalOperatorNewline: 'before',
      expressionWidth: 50,
      linesBetweenQueries: 1,
    })
  } catch {
    return sql
  }
}
