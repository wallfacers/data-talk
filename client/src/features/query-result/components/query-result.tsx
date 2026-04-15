import { Badge } from "@/components/ui/badge"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyTitle,
} from "@/components/ui/empty"
import { ResultTable } from "@/features/query-result/components/result-table"
import type { QueryResultData } from "@/features/query-result/types"
import { DatabaseIcon } from "lucide-react"

interface QueryResultProps {
  data: QueryResultData | null
}

export function QueryResult({ data }: QueryResultProps) {
  if (!data || data.rows.length === 0) {
    return (
      <div className="flex h-full items-center justify-center bg-muted/5">
        <Empty className="border-0">
          <EmptyHeader>
            <div className="mb-4 flex size-12 items-center justify-center rounded-2xl bg-muted text-muted-foreground/60">
              <DatabaseIcon className="size-6" />
            </div>
            <EmptyTitle className="text-lg">准备就绪</EmptyTitle>
            <EmptyDescription>查询结果将在这里实时展现</EmptyDescription>
          </EmptyHeader>
        </Empty>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col bg-muted/5">
      <div className="flex items-center justify-between border-b px-6 py-4">
        <div className="flex items-center gap-3">
          <h3 className="text-sm font-semibold tracking-tight">查询结果</h3>
          <Badge variant="outline" className="h-5 px-1.5 font-mono text-[10px] uppercase">
            {data.rowCount} ROWS
          </Badge>
        </div>
        <span className="text-[11px] font-medium text-muted-foreground">
          {data.durationMs}ms
        </span>
      </div>
      <div className="flex-1 overflow-hidden p-6 pt-4">
        <ResultTable columns={data.columns} rows={data.rows} />
      </div>
    </div>
  )
}
