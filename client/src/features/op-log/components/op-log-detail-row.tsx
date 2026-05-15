import { useQuery } from '@tanstack/react-query'
import type { OpLogItem } from '@/services/api/connection-op-log'
import { getOpLogDetail, type OpLogDetail } from '@/services/api/connection-op-log'
import { BeforeStateRenderer } from './before-state-renderer'

interface OpLogDetailRowProps {
  item: OpLogItem
  connectionId: string
}

export function OpLogDetailRow({ item, connectionId }: OpLogDetailRowProps) {
  const { data: detail } = useQuery<OpLogDetail>({
    queryKey: ['op-log-detail', connectionId, item.id],
    queryFn: () => getOpLogDetail(connectionId, item.id),
  })

  return (
    <div className="space-y-3 p-4">
      {detail?.originalSql && (
        <div>
          <div className="mb-1 text-xs font-medium text-text-muted">Original SQL</div>
          <pre className="overflow-x-auto rounded-md bg-bg-subtle p-2 font-mono text-[13px] leading-[18px] text-text-base">
            {detail.originalSql}
          </pre>
        </div>
      )}
      {detail?.inverseSql && (
        <div>
          <div className="mb-1 text-xs font-medium text-text-muted">Inverse SQL</div>
          <pre className="overflow-x-auto rounded-md bg-bg-subtle p-2 font-mono text-[13px] leading-[18px] text-text-base">
            {detail.inverseSql}
          </pre>
        </div>
      )}
      {detail?.beforeState && (
        <div>
          <div className="mb-1 text-xs font-medium text-text-muted">Before State</div>
          <BeforeStateRenderer
            beforeStateJson={detail.beforeState}
            operation={item.operation}
          />
        </div>
      )}
      <div className="flex gap-6 text-xs text-text-muted">
        {item.sessionTitle && <span>Session: {item.sessionTitle}</span>}
        <span>Expires: {new Date(item.expiresAt).toLocaleString()}</span>
      </div>
    </div>
  )
}
