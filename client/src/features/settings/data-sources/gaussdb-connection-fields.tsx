import { type FC } from 'react'
import { useI18n } from '@/i18n/use-i18n'

interface Props {
  kindInput: string
}

/**
 * GaussDB connection fields: PG-compatible database, no multi-mode selector needed.
 * The standard host/port/username/password fields are handled by the parent form.
 * Default port is 8000.
 */
export const GaussDBConnectionFields: FC<Props> = ({ kindInput: _kindInput }) => {
  const { t } = useI18n()

  return (
    <div className="col-start-2">
      <span className="text-xs text-muted-foreground">
        {t('connection.kind.gaussdb.defaultPort')}
      </span>
    </div>
  )
}
