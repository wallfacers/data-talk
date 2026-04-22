import { format, type SqlLanguage } from 'sql-formatter'

export function resolveSqlFormatterLanguage(connectionKind: string | null | undefined): SqlLanguage {
  switch (connectionKind?.trim().toLowerCase()) {
    case 'postgres':
    case 'postgresql':
      return 'postgresql'
    case 'mysql':
      return 'mysql'
    case 'h2':
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
