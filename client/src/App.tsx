import { Layout } from "@/app/layout"
import { useChat } from "@/features/chat/hooks/use-chat"
import { ChatArea } from "@/features/chat/components/chat-area"
import { QueryResult } from "@/features/query-result/components/query-result"
import { ChatInput } from "@/features/chat/components/chat-input"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { DatabaseIcon } from "lucide-react"
import type { QueryResultData } from "@/features/query-result/types"

function App() {
  const { messages, isLoading, queryResult, sendMessage } = useChat()

  const resultData: QueryResultData | null = queryResult
    ? {
        columns: queryResult.columns,
        rows: queryResult.rows,
        rowCount: queryResult.rowCount,
        durationMs: queryResult.durationMs,
      }
    : null

  const isInitialState = messages.length === 0 && !isLoading

  return (
    <Layout>
      {isInitialState ? (
        <div className="flex flex-1 flex-col items-center justify-center p-4">
          <div className="w-full max-w-3xl space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
            <Empty className="border-0">
              <EmptyHeader>
                <div className="mb-4 flex size-16 items-center justify-center rounded-3xl bg-primary/5 text-primary shadow-sm ring-1 ring-primary/10">
                  <DatabaseIcon className="size-8" />
                </div>
                <EmptyTitle className="text-3xl font-bold tracking-tight text-center">您好，我是数据对话助手</EmptyTitle>
                <EmptyDescription className="text-lg text-muted-foreground mt-2 text-center">
                  我可以帮您查询数据库、分析数据并提供洞察。<br />
                  试着输入：“查询最近一周的新用户”
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
            <div className="px-4">
              <ChatInput onSubmit={sendMessage} disabled={isLoading} autoFocus />
            </div>
          </div>
        </div>
      ) : (
        <div className="grid flex-1 gap-4 overflow-hidden md:grid-cols-2 animate-in fade-in duration-500">
          <div className="overflow-hidden rounded-xl border bg-card shadow-sm">
            <ChatArea
              messages={messages}
              isLoading={isLoading}
              onSend={sendMessage}
            />
          </div>
          <div className="overflow-hidden rounded-xl border bg-card shadow-sm">
            <QueryResult data={resultData} />
          </div>
        </div>
      )}
    </Layout>
  )
}

export default App
