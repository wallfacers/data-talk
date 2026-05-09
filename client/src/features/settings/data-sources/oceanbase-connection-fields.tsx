import { type FC } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface Props {
  tenant: string
  onTenantChange: (value: string) => void
  cluster: string
  onClusterChange: (value: string) => void
}

/**
 * OceanBase-private connection fields: tenant (required) and cluster (optional).
 * These fields appear only when kind === 'oceanbase'.
 */
export const OceanBaseConnectionFields: FC<Props> = ({
  tenant,
  onTenantChange,
  cluster,
  onClusterChange,
}) => {
  const { t } = useI18n()

  return (
    <>
      <div className="grid grid-cols-[120px_1fr] items-center gap-2">
        <Label>{t('connection.kind.oceanbase.tenant')}</Label>
        <Input
          aria-label={t('connection.kind.oceanbase.tenant')}
          value={tenant}
          placeholder={t('connection.kind.oceanbase.tenantPlaceholder')}
          onChange={(e) => onTenantChange(e.target.value)}
          required
        />
      </div>
      <div className="grid grid-cols-[120px_1fr] items-center gap-2">
        <Label>{t('connection.kind.oceanbase.cluster')}</Label>
        <Input
          aria-label={t('connection.kind.oceanbase.cluster')}
          value={cluster}
          placeholder={t('connection.kind.oceanbase.clusterPlaceholder')}
          onChange={(e) => onClusterChange(e.target.value)}
        />
      </div>
    </>
  )
}
