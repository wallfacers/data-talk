import { useRef, useEffect, useCallback } from 'react'
import { EditorView, basicSetup } from 'codemirror'
import { sql } from '@codemirror/lang-sql'
import { keymap } from '@codemirror/view'
import { Prec } from '@codemirror/state'
import { PlayIcon, AlertTriangleIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { StageTab } from '@/stores/stage-store'
import { useConnectionStore } from '@/features/connection/store'
import { useSqlExecute } from '../hooks/use-sql-execute'
import { useChannel } from '@/services/channel/use-channel'
import type { SqlResult } from '@/services/api/sql'

type QueryEditorPayload = {
  sql?: string
  source?: 'ai' | 'user'
  connectionId?: string
}

function SqlEditor({
  initialValue,
  editorRef,
  onRun,
}: {
  initialValue: string
  editorRef: React.MutableRefObject<EditorView | undefined>
  onRun: () => void
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const onRunRef = useRef(onRun)
  onRunRef.current = onRun

  useEffect(() => {
    if (!containerRef.current) return
    editorRef.current = new EditorView({
      doc: initialValue,
      extensions: [
        basicSetup,
        sql(),
        Prec.high(
          keymap.of([{
            key: 'Ctrl-Enter',
            mac: 'Cmd-Enter',
            run: () => { onRunRef.current(); return true },
          }])
        ),
      ],
      parent: containerRef.current,
    })
    return () => { editorRef.current?.destroy(); editorRef.current = undefined }
  }, []) // intentional: mount-only, refs keep values fresh

  return (
    <div
      ref={containerRef}
      className="h-full overflow-auto [&_.cm-editor]:h-full [&_.cm-editor]:outline-none [&_.cm-scroller]:font-mono [&_.cm-scroller]:text-sm"
    />
  )
}

function ResultTable({ result }: { result: SqlResult }) {
  return (
    <div className="flex h-full flex-col">
      <div className="flex shrink-0 items-center border-b border-border/50 px-3 py-1.5 text-xs text-muted-foreground">
        <span>
          {result.truncated
            ? `前 ${result.rowCount} 行（已截断）`
            : `${result.rowCount} 行`}{' '}
          · {result.executionMs}ms
        </span>
      </div>
      <div className="flex-1 overflow-auto">
        <table className="w-full border-collapse text-xs">
          <thead className="sticky top-0 bg-muted/50">
            <tr>
              {result.columns.map((col) => (
                <th
                  key={col}
                  className="whitespace-nowrap border-b border-border/50 px-3 py-2 text-left font-medium"
                >
                  {col}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {result.rows.map((row, i) => (
              <tr key={i} className="border-b border-border/30 last:border-0 hover:bg-muted/30">
                {row.map((cell, j) => (
                  <td key={j} className="max-w-[300px] truncate whitespace-nowrap px-3 py-1.5">
                    {cell === null ? (
                      <span className="italic text-muted-foreground/50">null</span>
                    ) : (
                      String(cell)
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export function QueryEditorTab({ tab }: { tab: StageTab }) {
  const payload = tab.payload as QueryEditorPayload
  const editorRef = useRef<EditorView | undefined>(undefined)
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const connectionId = payload.connectionId ?? tab.connectionId ?? activeConnectionId ?? ''
  const source = payload.source ?? 'user'
  const isAiSource = source === 'ai'

  const { execute, result, risk, status, reset } = useSqlExecute()
  const { sendMessage } = useChannel()

  const handleRun = useCallback(() => {
    const sqlText = editorRef.current?.state.doc.toString() ?? ''
    if (!sqlText.trim() || !connectionId) return
    execute(sqlText, connectionId, source)
  }, [execute, connectionId, source])

  const handleSendToAi = useCallback(() => {
    const sqlText = editorRef.current?.state.doc.toString() ?? ''
    sendMessage([{
      type: 'text',
      text: `请帮我检查这段 SQL 是否安全，如果可以执行请帮我执行：\n\`\`\`sql\n${sqlText}\n\`\`\``,
    }])
    reset()
  }, [sendMessage, reset])

  return (
    <div className="flex h-full flex-col">
      {/* Toolbar */}
      <div className="flex shrink-0 items-center justify-between border-b border-border/50 px-3 py-2">
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          {isAiSource && (
            <span className="rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium text-purple-600 dark:bg-purple-900/30 dark:text-purple-400">
              AI 生成
            </span>
          )}
          <span>{connectionId || '未选择连接'}</span>
        </div>
        <Button
          size="sm"
          variant="default"
          disabled={status === 'running' || !connectionId}
          onClick={handleRun}
          className="h-7 gap-1.5 text-xs"
        >
          <PlayIcon className="size-3.5" />
          {isAiSource ? '直接执行' : 'Ctrl+Enter 运行'}
        </Button>
      </div>

      {/* SQL Editor */}
      <div className="min-h-0 flex-1 overflow-hidden border-b border-border/50">
        <SqlEditor initialValue={payload.sql ?? ''} editorRef={editorRef} onRun={handleRun} />
      </div>

      {/* Result panel */}
      <div className="min-h-0 flex-1 overflow-auto">
        {status === 'idle' && (
          <div className="flex h-full items-center justify-center text-xs text-muted-foreground">
            运行 SQL 后在此查看结果
          </div>
        )}
        {status === 'running' && (
          <div className="flex h-full items-center justify-center text-xs text-muted-foreground">
            执行中…
          </div>
        )}
        {status === 'success' && result && <ResultTable result={result} />}
        {status === 'risk_blocked' && risk && (
          <div className="flex h-full flex-col items-center justify-center gap-4 p-6">
            <div className="flex items-center gap-2 text-amber-600 dark:text-amber-400">
              <AlertTriangleIcon className="size-5" />
              <span className="text-sm font-medium">高风险操作</span>
            </div>
            <p className="text-center text-sm text-muted-foreground">{risk.riskReason}</p>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" onClick={reset}>
                取消
              </Button>
              <Button size="sm" onClick={handleSendToAi}>
                发给 AI 审查 →
              </Button>
            </div>
          </div>
        )}
        {status === 'error' && (
          <div className="flex h-full items-center justify-center p-4 text-xs text-destructive">
            执行失败，请检查 SQL 语法
          </div>
        )}
      </div>
    </div>
  )
}
