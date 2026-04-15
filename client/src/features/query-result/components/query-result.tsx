import { Badge } from "@/components/ui/badge"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
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
      <div className="flex h-full items-center justify-center p-4">
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <DatabaseIcon />
            </EmptyMedia>
            <EmptyTitle>暂无查询结果</EmptyTitle>
            <EmptyDescription>执行查询后结果将显示在这里</EmptyDescription>
          </EmptyHeader>
        </Empty>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col p-4">
      <div className="mb-3 flex items-center gap-2">
        <Badge variant="secondary">{data.rowCount} 行</Badge>
        <span className="text-xs text-muted-foreground">
          耗时 {data.durationMs}ms
        </span>
      </div>
      <div className="flex-1 overflow-hidden">
        <ResultTable columns={data.columns} rows={data.rows} />
      </div>
    </div>
  )
}
