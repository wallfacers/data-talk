import { useQuery } from '@tanstack/react-query'
import type { OpLogItem } from '@/services/api/connection-op-log'
import { getOpLogDetail, type OpLogDetail } from '@/services/api/connection-op-log'
import { useI18n } from '@/i18n/use-i18n'
import { BeforeStateRenderer } from './before-state-renderer'

function formatDateTime(ts: number): string {
  const d = new Date(ts)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  const s = String(d.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}:${s}`
}

interface OpLogDetailRowProps {
  item: OpLogItem
  connectionId: string
}

export function OpLogDetailRow({ item, connectionId }: OpLogDetailRowProps) {
  const { t } = useI18n()
  const now = Date.now()
  const expired = item.expiresAt < now

  const { data: detail } = useQuery<OpLogDetail>({
    queryKey: ['op-log-detail', connectionId, item.id],
    queryFn: () => getOpLogDetail(connectionId, item.id),
  })

  return (
    <div className="space-y-3 p-4">
      {detail?.originalSql && (
        <div>
          <div className="mb-1 text-xs font-medium text-text-muted">{t('opLog.detail.originalSql')}</div>
          <pre className="overflow-x-auto rounded-md bg-bg-subtle p-2 font-mono text-[13px] leading-[18px] text-text-base">
            {detail.originalSql}
          </pre>
        </div>
      )}
      {detail?.inverseSql && (
        <div>
          <div className="mb-1 text-xs font-medium text-text-muted">{t('opLog.detail.inverseSql')}</div>
          <pre className="overflow-x-auto rounded-md bg-bg-subtle p-2 font-mono text-[13px] leading-[18px] text-text-base">
            {detail.inverseSql}
          </pre>
        </div>
      )}
      {detail?.beforeState && (
        <div>
          <div className="mb-1 text-xs font-medium text-text-muted">{t('opLog.detail.beforeState')}</div>
          <BeforeStateRenderer
            beforeStateJson={detail.beforeState}
            operation={item.operation}
          />
        </div>
      )}
      <div className="flex gap-6 text-xs text-text-muted">
        {item.sessionTitle && <span>{t('opLog.detail.session', { title: item.sessionTitle })}</span>}
        <span>{t('opLog.detail.createdAt', { time: formatDateTime(item.createdAt) })}</span>
        <span>
          {t('opLog.detail.expires', { time: formatDateTime(item.expiresAt) })}
          {expired && ` (${t('opLog.detail.expired')})`}
        </span>
      </div>
    </div>
  )
}
