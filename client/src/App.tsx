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
        <div className="flex flex-1 flex-col items-center justify-center">
          <div className="w-full max-w-3xl space-y-8 animate-in fade-in slide-in-from-bottom-8 duration-1000 ease-out">
            <Empty className="border-0">
              <EmptyHeader>
                <div className="mb-6 flex size-20 items-center justify-center rounded-[2.5rem] bg-primary/5 text-primary shadow-sm ring-1 ring-primary/10">
                  <DatabaseIcon className="size-10" />
                </div>
                <EmptyTitle className="text-4xl font-bold tracking-tight text-center">Data Talk</EmptyTitle>
                <EmptyDescription className="text-xl text-muted-foreground mt-4 text-center max-w-md">
                  随时为您提供数据库查询、分析与洞察
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
            <div className="px-4">
              <ChatInput onSubmit={sendMessage} disabled={isLoading} autoFocus />
            </div>
          </div>
        </div>
      ) : (
        <div className="grid flex-1 gap-0 overflow-hidden md:grid-cols-2 animate-in fade-in duration-700">
          <div className="flex flex-col overflow-hidden bg-background">
            <ChatArea
              messages={messages}
              isLoading={isLoading}
              onSend={sendMessage}
            />
          </div>
          <div className="flex flex-col overflow-hidden border-l bg-muted/5">
            <QueryResult data={resultData} />
          </div>
        </div>
      )}
    </Layout>
  )
}

export default App
