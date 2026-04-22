import { useEffect, useMemo, useRef, useState } from 'react'
import { useQueryClient, type QueryClient } from '@tanstack/react-query'
import { SparklesIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { generateUuid } from '@/lib/uuid'
import { createSession, deleteSession } from '@/services/api/session'
import { ChannelClient } from '@/services/channel/channel-client'
import { invalidateSessionLists, STAGE_AI_SESSION_TITLE_PREFIX } from '@/features/session/hooks/use-sessions'
import { useSqlWorkbenchStore } from '../../stores/sql-workbench-store'
import { parseSqlOutline, resolveCurrentSqlOutlineStatement } from '../../utils/parse-sql-outline'
import { getSqlWorkbenchTabActions } from '../sql-workbench-tab'

type AiAssistPanelProps = {
  tabId: string | null
  tabTitle: string | null
  connectionName: string | null
  database: string | null
  schema: string | null
  lastError: string | null
  lastRunSummary: string | null
}

type AiAssistAction = 'explain' | 'optimize' | 'fix_error'

const actionLabels: Record<AiAssistAction, string> = {
  explain: 'Explain',
  optimize: 'Optimize',
  fix_error: 'Fix error',
}

const sessionIdByTabId = new Map<string, string>()
const pendingSessionByTabId = new Map<string, Promise<string | null>>()
const removedTabIds = new Set<string>()

export async function cleanupStageAiSessions(activeTabIds: Set<string>, queryClient?: QueryClient) {
  let changed = false
  const removals: Array<{ tabId: string; sessionId: string }> = []

  for (const [tabId, sessionId] of sessionIdByTabId.entries()) {
    if (activeTabIds.has(tabId)) {
      removedTabIds.delete(tabId)
      continue
    }
    removals.push({ tabId, sessionId })
  }

  for (const [tabId] of pendingSessionByTabId.entries()) {
    if (!activeTabIds.has(tabId)) {
      removedTabIds.add(tabId)
    }
  }

  for (const { tabId, sessionId } of removals) {
    removedTabIds.add(tabId)
    sessionIdByTabId.delete(tabId)
    await deleteSession(sessionId)
    changed = true
  }

  if (changed && queryClient) {
    invalidateSessionLists(queryClient)
  }
}

async function ensureStageAiSession(tabId: string, tabTitle: string | null, queryClient: QueryClient) {
  removedTabIds.delete(tabId)

  const existing = sessionIdByTabId.get(tabId)
  if (existing) return existing

  const pending = pendingSessionByTabId.get(tabId)
  if (pending) return pending

  const createPromise = (async () => {
    const title = `${STAGE_AI_SESSION_TITLE_PREFIX}${tabTitle?.trim() || tabId}`
    const session = await createSession(undefined, title)

    if (removedTabIds.has(tabId)) {
      await deleteSession(session.id)
      invalidateSessionLists(queryClient)
      return null
    }

    sessionIdByTabId.set(tabId, session.id)
    invalidateSessionLists(queryClient)
    return session.id
  })().finally(() => {
    pendingSessionByTabId.delete(tabId)
  })

  pendingSessionByTabId.set(tabId, createPromise)
  return createPromise
}

function getApiBaseUrl() {
  const env = (import.meta as any).env?.VITE_API_BASE_URL
  if (typeof env === 'string' && env.length > 0) return env.replace(/\/$/, '')
  return ''
}

function buildPrompt(
  action: string,
  sqlText: string,
  connectionName: string | null,
  database: string | null,
  schema: string | null,
  lastError: string | null,
  lastRunSummary: string | null,
) {
  const contextLines = [
    connectionName ? `Connection: ${connectionName}` : null,
    database ? `Database: ${database}` : null,
    schema ? `Schema: ${schema}` : null,
    lastRunSummary ? `Recent run: ${lastRunSummary}` : null,
    lastError ? `Recent error: ${lastError}` : null,
  ].filter(Boolean)

  return [
    `You are helping with SQL workbench task: ${action}.`,
    'Respond concisely.',
    'If you provide SQL, include it in a fenced sql block.',
    '',
    'Current SQL:',
    '```sql',
    sqlText.trim() || '-- empty',
    '```',
    '',
    'Context:',
    ...(contextLines.length > 0 ? contextLines.map((line) => `- ${line}`) : ['- No extra context available']),
  ].join('\n')
}

function extractSqlFence(text: string) {
  const fencedSql = text.match(/```sql\s*([\s\S]*?)```/i)
  if (fencedSql?.[1]) return fencedSql[1].trim()
  const fencedAny = text.match(/```(?:\w+)?\s*([\s\S]*?)```/)
  return fencedAny?.[1]?.trim() ?? null
}

function insertTextAtPosition(value: string, insertedText: string, lineNumber: number, column: number) {
  const lines = value.split(/\r\n|\r|\n/)
  while (lines.length < lineNumber) {
    lines.push('')
  }

  const lineIndex = Math.max(0, lineNumber - 1)
  const line = lines[lineIndex] ?? ''
  const before = line.slice(0, Math.max(0, column - 1))
  const after = line.slice(Math.max(0, column - 1))
  const insertedLines = insertedText.split(/\r\n|\r|\n/)

  if (insertedLines.length === 1) {
    lines[lineIndex] = `${before}${insertedText}${after}`
    return lines.join('\n')
  }

  const firstLine = `${before}${insertedLines[0]}`
  const lastLine = `${insertedLines[insertedLines.length - 1]}${after}`
  const middleLines = insertedLines.slice(1, -1)

  lines.splice(lineIndex, 1, firstLine, ...middleLines, lastLine)
  return lines.join('\n')
}

function replaceSqlStatementRange(value: string, startLine: number, endLine: number, insertedText: string) {
  const lines = value.split(/\r\n|\r|\n/)
  while (lines.length < endLine) {
    lines.push('')
  }

  return [
    ...lines.slice(0, Math.max(0, startLine - 1)),
    ...insertedText.split(/\r\n|\r|\n/),
    ...lines.slice(Math.max(0, endLine)),
  ].join('\n')
}

export function AiAssistPanel({
  tabId,
  tabTitle,
  connectionName,
  database,
  schema,
  lastError,
  lastRunSummary,
}: AiAssistPanelProps) {
  const queryClient = useQueryClient()
  const clientId = useMemo(() => generateUuid(), [])
  const [sessionId, setSessionId] = useState<string | null>(tabId ? sessionIdByTabId.get(tabId) ?? null : null)
  const [responseText, setResponseText] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [suggestedSql, setSuggestedSql] = useState<string | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const responseRef = useRef('')

  const workbenchTab = useSqlWorkbenchStore((state) => (tabId ? state.tabsById[tabId] ?? null : null))
  const sqlText = workbenchTab?.sqlText ?? ''
  const cursor = workbenchTab?.cursor ?? { line: 1, column: 1 }
  const setSqlText = useSqlWorkbenchStore((state) => state.setSqlText)
  const setCursor = useSqlWorkbenchStore((state) => state.setCursor)

  const currentStatement = useMemo(() => {
    if (!workbenchTab) return null
    const statements = parseSqlOutline(workbenchTab.sqlText)
    return resolveCurrentSqlOutlineStatement(statements, workbenchTab.cursor.line, Math.max(1, workbenchTab.sqlText.split(/\r\n|\r|\n/).length))
  }, [workbenchTab])

  useEffect(() => {
    if (!tabId) return
    setSessionId(sessionIdByTabId.get(tabId) ?? null)
  }, [tabId])

  useEffect(() => {
    responseRef.current = responseText
  }, [responseText])

  async function runAction(action: AiAssistAction) {
    if (!tabId) return
    setError(null)
    setSuggestedSql(null)
    setConfirmOpen(false)
    setResponseText('')
    responseRef.current = ''
    setStreaming(true)

    try {
      const aiSessionId = await ensureStageAiSession(tabId, tabTitle, queryClient)
      if (!aiSessionId) return
      setSessionId(aiSessionId)

      const prompt = buildPrompt(
        actionLabels[action],
        sqlText,
        connectionName,
        database,
        schema,
        lastError,
        lastRunSummary,
      )
      const client = new ChannelClient({
        baseUrl: getApiBaseUrl(),
        sessionId: aiSessionId,
        clientId,
      })

      await client.sendMessage(
        [
          {
            type: 'text',
            id: generateUuid(),
            sessionID: aiSessionId,
            messageID: `pending-${generateUuid()}`,
            text: prompt,
            metadata: {},
          },
        ] as any,
        (evt) => {
          if (evt.event === 'message.part.created') {
            const part = (evt.data as { part?: { text?: string } })?.part
            const text = typeof part?.text === 'string' ? part.text : ''
            if (text) {
              responseRef.current = text
              setResponseText(text)
            }
          }
          if (evt.event === 'message.part.delta') {
            const data = evt.data as { field?: string; delta?: string }
            if (data.field === 'text' && typeof data.delta === 'string') {
              responseRef.current += data.delta
              setResponseText(responseRef.current)
            }
          }
          if (evt.event === 'session.error') {
            const message = (evt.data as { error?: string })?.error ?? 'AI request failed'
            setError(message)
          }
        },
      )

      const block = extractSqlFence(responseRef.current)
      if (block) {
        setSuggestedSql(block)
        setConfirmOpen(true)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'AI request failed')
    } finally {
      setStreaming(false)
    }
  }

  function handleInsertAtCursor() {
    if (!tabId || !suggestedSql) return
    const actions = getSqlWorkbenchTabActions(tabId)
    if (actions) {
      actions.insertAtCursor(suggestedSql)
    } else {
      setSqlText(tabId, insertTextAtPosition(sqlText, suggestedSql, cursor.line, cursor.column))
      setCursor(tabId, cursor.line, cursor.column + suggestedSql.length)
    }
    setConfirmOpen(false)
  }

  function handleReplaceSelection() {
    if (!tabId || !suggestedSql) return
    const actions = getSqlWorkbenchTabActions(tabId)
    if (actions) {
      actions.replaceSelection(suggestedSql)
    } else if (currentStatement) {
      setSqlText(tabId, replaceSqlStatementRange(sqlText, currentStatement.line, currentStatement.endLine, suggestedSql))
      setCursor(tabId, currentStatement.line, 1)
    } else {
      setSqlText(tabId, suggestedSql)
      setCursor(tabId, 1, 1)
    }
    setConfirmOpen(false)
  }

  const isReady = Boolean(sessionId)
  const canShowFixError = Boolean(lastError)

  if (!tabId) {
    return (
      <div data-testid="ai-assist-panel" className="rounded-lg border border-dashed border-border/60 bg-background/60 px-3 py-4 text-xs text-muted-foreground">
        Open a SQL tab to start an AI assist session.
      </div>
    )
  }

  return (
    <div data-testid="ai-assist-panel" className="space-y-3">
      <div className="space-y-2">
        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0">
            <div className="text-sm font-medium text-foreground">AI Assist</div>
            <div className="text-xs text-muted-foreground">
              {tabTitle ? `Tab: ${tabTitle}` : 'No active SQL tab'}
            </div>
          </div>
          <Badge variant="outline" className={cn('shrink-0', isReady ? 'border-emerald-500/30 text-emerald-700' : '')}>
            <SparklesIcon className="mr-1 size-3" />
            {isReady ? 'Ready' : 'Idle'}
          </Badge>
        </div>

        <div className="space-y-2">
          <div className="grid grid-cols-2 gap-2">
            <Button type="button" variant="outline" onClick={() => void runAction('explain')} disabled={streaming}>
              Explain
            </Button>
            <Button type="button" variant="outline" onClick={() => void runAction('optimize')} disabled={streaming}>
              Optimize
            </Button>
          </div>
          {canShowFixError ? (
            <Button type="button" variant="secondary" className="w-full" onClick={() => void runAction('fix_error')} disabled={streaming}>
              Fix error
            </Button>
          ) : null}
        </div>
      </div>

      <div className="rounded-lg border border-border/60 bg-background/80 p-3">
        <div className="mb-2 flex items-center justify-between gap-3">
          <div className="text-xs font-medium text-foreground">Response</div>
          <Badge variant="outline">{streaming ? 'Streaming' : 'Idle'}</Badge>
        </div>
        {responseText ? (
          <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words text-xs text-foreground">{responseText}</pre>
        ) : (
          <div className="text-xs text-muted-foreground">No AI response yet.</div>
        )}
        {error ? <div className="mt-2 text-xs text-destructive">{error}</div> : null}
      </div>

      <Dialog open={confirmOpen && Boolean(suggestedSql)} onOpenChange={(open) => setConfirmOpen(open)}>
        <DialogContent className="max-w-[560px]">
          <DialogHeader>
            <DialogTitle>Insert suggested SQL?</DialogTitle>
            <DialogDescription>We found a fenced SQL block in the response.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Textarea value={suggestedSql ?? ''} readOnly className="min-h-36 font-mono text-xs" />
          </div>
          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" onClick={handleInsertAtCursor} disabled={!suggestedSql}>
              Insert at cursor
            </Button>
            <Button type="button" variant="secondary" onClick={handleReplaceSelection} disabled={!suggestedSql}>
              Replace selection
            </Button>
            <Button type="button" variant="ghost" onClick={() => setConfirmOpen(false)}>
              Cancel
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
