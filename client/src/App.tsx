import { Layout } from "@/app/layout"
import { useChat } from "@/features/chat/hooks/use-chat"
import { ChatArea } from "@/features/chat/components/chat-area"
import { QueryResult } from "@/features/query-result/components/query-result"
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

  return (
    <Layout>
      <div className="grid flex-1 gap-4 overflow-hidden md:grid-cols-2">
        <div className="overflow-hidden rounded-xl border bg-card">
          <ChatArea
            messages={messages}
            isLoading={isLoading}
            onSend={sendMessage}
          />
        </div>
        <div className="overflow-hidden rounded-xl border bg-card">
          <QueryResult data={resultData} />
        </div>
      </div>
    </Layout>
  )
}

export default App
