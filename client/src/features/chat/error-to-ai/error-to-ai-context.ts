export interface ErrorContext {
  title: string
  connectionName?: string | null
  connectionKind?: string | null
  database?: string | null
  schema?: string | null
  statementText?: string | null
  errorMessage?: string | null
  extraContext?: string | null
}

const NOT_SET = '未设置'

export function buildErrorMarkdown(ctx: ErrorContext): string {
  const lines: string[] = []

  lines.push(`**${ctx.title}**`)
  lines.push('')

  if (ctx.connectionName) {
    const kind = ctx.connectionKind ? ` (${ctx.connectionKind})` : ''
    lines.push(`- **连接**: ${ctx.connectionName}${kind}`)
  }

  if (ctx.database) {
    lines.push(`- **数据库**: ${ctx.database}`)
  } else if (ctx.database === null) {
    lines.push(`- **数据库**: ${NOT_SET}`)
  }

  if (ctx.schema) {
    lines.push(`- **Schema**: ${ctx.schema}`)
  } else if (ctx.schema === null) {
    lines.push(`- **Schema**: ${NOT_SET}`)
  }

  if (ctx.statementText) {
    lines.push('')
    lines.push('- **执行的 SQL**:')
    lines.push('')
    lines.push('```sql')
    lines.push(ctx.statementText)
    lines.push('```')
  }

  if (ctx.errorMessage) {
    lines.push('')
    lines.push('- **错误信息**:')
    lines.push('')
    lines.push('```')
    lines.push(ctx.errorMessage)
    lines.push('```')
  }

  if (ctx.extraContext) {
    lines.push('')
    lines.push(ctx.extraContext)
  }

  return lines.join('\n')
}
