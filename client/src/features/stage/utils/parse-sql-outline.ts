import mysqlKeywords from '../sql-dialects/mysql-keywords.json'
import postgresKeywords from '../sql-dialects/postgres-keywords.json'
import h2Keywords from '../sql-dialects/h2-keywords.json'

export type SqlOutlineStatement = {
  line: number
  kind: string
  summary: string
  highRiskHint: boolean
}

type SqlOutlineStatementRange = SqlOutlineStatement & {
  endLine: number
}

const KEYWORDS = new Set([...mysqlKeywords, ...postgresKeywords, ...h2Keywords])
const HIGH_RISK_KINDS = new Set(['DROP', 'TRUNCATE', 'ALTER'])

export function parseSqlOutline(sql: string): SqlOutlineStatement[] {
  const statements: Array<{ line: number; text: string }> = []
  let current = ''
  let currentStartLine = 1
  let hasContent = false
  let line = 1
  let inSingleQuote = false
  let inDoubleQuote = false
  let inBacktick = false
  let inLineComment = false
  let blockCommentDepth = 0
  let inDollarQuote: string | null = null

  for (let i = 0; i < sql.length; i += 1) {
    const ch = sql[i]
    const next = sql[i + 1]

    if (inLineComment) {
      if (ch === '\n') {
        line += 1
        inLineComment = false
        if (hasContent) current += ch
      }
      continue
    }

    if (blockCommentDepth > 0) {
      if (ch === '\n') {
        line += 1
        if (hasContent) current += ch
        continue
      }
      if (ch === '*' && next === '/') {
        blockCommentDepth -= 1
        i += 1
      }
      continue
    }

    if (inDollarQuote) {
      if (sql.startsWith(inDollarQuote, i)) {
        current += inDollarQuote
        i += inDollarQuote.length - 1
        inDollarQuote = null
        continue
      }
      if (ch === '\n') line += 1
      current += ch
      continue
    }

    if (inSingleQuote) {
      current += ch
      if (ch === '\n') line += 1
      if (ch === "'" && next === "'") {
        current += next
        i += 1
        continue
      }
      if (ch === "'") {
        inSingleQuote = false
      }
      continue
    }

    if (inDoubleQuote) {
      current += ch
      if (ch === '\n') line += 1
      if (ch === '"' && next === '"') {
        current += next
        i += 1
        continue
      }
      if (ch === '"') {
        inDoubleQuote = false
      }
      continue
    }

    if (inBacktick) {
      current += ch
      if (ch === '\n') line += 1
      if (ch === '`' && next === '`') {
        current += next
        i += 1
        continue
      }
      if (ch === '`') {
        inBacktick = false
      }
      continue
    }

    if (ch === '-' && next === '-') {
      inLineComment = true
      i += 1
      continue
    }
    if (ch === '/' && next === '*') {
      blockCommentDepth += 1
      i += 1
      continue
    }

    const dollarQuoteTag = readDollarQuoteTag(sql, i)
    if (dollarQuoteTag) {
      if (!hasContent) {
        currentStartLine = line
        hasContent = true
      }
      current += dollarQuoteTag
      i += dollarQuoteTag.length - 1
      inDollarQuote = dollarQuoteTag
      continue
    }

    if (ch === "'") {
      if (!hasContent) {
        currentStartLine = line
        hasContent = true
      }
      current += ch
      inSingleQuote = true
      continue
    }
    if (ch === '"') {
      if (!hasContent) {
        currentStartLine = line
        hasContent = true
      }
      current += ch
      inDoubleQuote = true
      continue
    }
    if (ch === '`') {
      if (!hasContent) {
        currentStartLine = line
        hasContent = true
      }
      current += ch
      inBacktick = true
      continue
    }

    if (ch === ';') {
      pushStatement(statements, current, currentStartLine)
      current = ''
      hasContent = false
      currentStartLine = line
      continue
    }

    if (/\s/.test(ch)) {
      if (ch === '\n') line += 1
      if (hasContent) current += ch
      continue
    }

    if (!hasContent) {
      currentStartLine = line
      hasContent = true
    }
    current += ch
  }

  pushStatement(statements, current, currentStartLine)

  return statements.map(({ line: statementLine, text }) => {
    const tokens = extractSqlTokens(text)
    const firstToken = tokens[0] ?? 'UNKNOWN'
    const kind = KEYWORDS.has(firstToken) ? firstToken : firstToken.toUpperCase()
    const summary = text.trim().replace(/\s+/g, ' ').replace(/;$/, '')
    const highRiskHint =
      HIGH_RISK_KINDS.has(kind) || ((kind === 'UPDATE' || kind === 'DELETE') && !tokens.includes('WHERE'))

    return {
      line: statementLine,
      kind,
      summary,
      highRiskHint,
    }
  })
}

export function resolveCurrentSqlOutlineStatement(
  statements: SqlOutlineStatement[],
  cursorLine: number,
  totalLines: number,
): SqlOutlineStatementRange | null {
  if (!statements.length) return null

  const sorted = [...statements].sort((left, right) => left.line - right.line)
  let currentIndex = -1

  for (let index = 0; index < sorted.length; index += 1) {
    if (sorted[index].line <= cursorLine) {
      currentIndex = index
    } else {
      break
    }
  }

  if (currentIndex < 0) {
    currentIndex = 0
  }

  const current = sorted[currentIndex]
  const next = sorted[currentIndex + 1]
  const endLine = Math.max(current.line, (next?.line ?? totalLines + 1) - 1)

  return {
    ...current,
    endLine,
  }
}

function pushStatement(
  statements: Array<{ line: number; text: string }>,
  text: string,
  line: number,
) {
  const trimmed = text.trim()
  if (!trimmed) return
  statements.push({ line, text: trimmed })
}

function extractSqlTokens(statement: string) {
  const tokens: string[] = []
  let current = ''
  let inSingleQuote = false
  let inDoubleQuote = false
  let inBacktick = false
  let inLineComment = false
  let blockCommentDepth = 0
  let inDollarQuote: string | null = null

  for (let i = 0; i < statement.length; i += 1) {
    const ch = statement[i]
    const next = statement[i + 1]

    if (inLineComment) {
      if (ch === '\n') inLineComment = false
      continue
    }
    if (blockCommentDepth > 0) {
      if (ch === '*' && next === '/') {
        blockCommentDepth -= 1
        i += 1
      }
      continue
    }
    if (inDollarQuote) {
      if (statement.startsWith(inDollarQuote, i)) {
        i += inDollarQuote.length - 1
        inDollarQuote = null
      }
      continue
    }
    if (inSingleQuote) {
      if (ch === "'" && next === "'") {
        i += 1
        continue
      }
      if (ch === "'") inSingleQuote = false
      continue
    }
    if (inDoubleQuote) {
      if (ch === '"' && next === '"') {
        i += 1
        continue
      }
      if (ch === '"') inDoubleQuote = false
      continue
    }
    if (inBacktick) {
      if (ch === '`' && next === '`') {
        i += 1
        continue
      }
      if (ch === '`') inBacktick = false
      continue
    }

    if (ch === '-' && next === '-') {
      flushToken(tokens, current)
      current = ''
      inLineComment = true
      i += 1
      continue
    }
    if (ch === '/' && next === '*') {
      flushToken(tokens, current)
      current = ''
      blockCommentDepth += 1
      i += 1
      continue
    }

    const dollarQuoteTag = readDollarQuoteTag(statement, i)
    if (dollarQuoteTag) {
      flushToken(tokens, current)
      current = ''
      inDollarQuote = dollarQuoteTag
      i += dollarQuoteTag.length - 1
      continue
    }

    if (ch === "'") {
      flushToken(tokens, current)
      current = ''
      inSingleQuote = true
      continue
    }
    if (ch === '"') {
      flushToken(tokens, current)
      current = ''
      inDoubleQuote = true
      continue
    }
    if (ch === '`') {
      flushToken(tokens, current)
      current = ''
      inBacktick = true
      continue
    }

    if (/[A-Za-z0-9_$]/.test(ch)) {
      current += ch
    } else {
      flushToken(tokens, current)
      current = ''
    }
  }

  flushToken(tokens, current)
  return tokens
}

function flushToken(tokens: string[], token: string) {
  if (!token) return
  tokens.push(token.toUpperCase())
}

function readDollarQuoteTag(sql: string, index: number) {
  if (sql[index] !== '$') return null
  if (sql[index + 1] === '$') return '$$'

  const end = sql.indexOf('$', index + 1)
  if (end <= index + 1) return null
  const tag = sql.slice(index, end + 1)
  return /^\$[A-Za-z_][A-Za-z0-9_]*\$$/.test(tag) ? tag : null
}
