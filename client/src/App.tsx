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
      <div className="flex h-[calc(100vh-3rem)]">
        <div className="w-1/2 border-r">
          <ChatArea
            messages={messages}
            isLoading={isLoading}
            onSend={sendMessage}
          />
        </div>
        <div className="w-1/2">
          <QueryResult data={resultData} />
        </div>
      </div>
    </Layout>
  )
}

export default App
