import { useState } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PasswordInput } from '@/components/ui/password-input'
import type { AuthScheme, CredentialCreateRequest } from './api/credential-api'

const SCHEMES: AuthScheme[] = ['none', 'bearer', 'api_key_header', 'api_key_query', 'basic']

interface Props {
  onSubmit: (req: CredentialCreateRequest) => void
  submitting?: boolean
}

export function CredentialForm({ onSubmit, submitting }: Props) {
  const { t } = useI18n()
  const [name, setName] = useState('')
  const [scheme, setScheme] = useState<AuthScheme>('none')
  const [bearerToken, setBearerToken] = useState('')
  const [apiKeyName, setApiKeyName] = useState('')
  const [apiKeyValue, setApiKeyValue] = useState('')
  const [basicUsername, setBasicUsername] = useState('')
  const [basicPassword, setBasicPassword] = useState('')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    let configNonSecret: Record<string, string> = {}
    let secret: string | null = null

    if (scheme === 'bearer') {
      secret = bearerToken
    } else if (scheme === 'api_key_header') {
      configNonSecret = { headerName: apiKeyName }
      secret = apiKeyValue
    } else if (scheme === 'api_key_query') {
      configNonSecret = { queryName: apiKeyName }
      secret = apiKeyValue
    } else if (scheme === 'basic') {
      configNonSecret = { username: basicUsername }
      secret = basicPassword
    }

    onSubmit({ name, authScheme: scheme, configNonSecret, secret })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <Label htmlFor="cred-name">{t('ingestion.credential.name')}</Label>
        <Input
          id="cred-name"
          required
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="mt-1"
        />
      </div>

      <div>
        <Label>{t('ingestion.credential.scheme.label')}</Label>
        <div className="mt-1 grid grid-cols-2 gap-2">
          {SCHEMES.map((s) => (
            <label
              key={s}
              htmlFor={`scheme-${s}`}
              className="flex items-center gap-2 cursor-pointer text-sm"
            >
              <input
                type="radio"
                id={`scheme-${s}`}
                name="auth-scheme"
                value={s}
                checked={scheme === s}
                onChange={() => setScheme(s)}
                className="accent-interaction-focusRing"
              />
              {t(`ingestion.credential.scheme.${s}`)}
            </label>
          ))}
        </div>
      </div>

      {scheme === 'bearer' && (
        <div>
          <Label htmlFor="bearer-token">{t('ingestion.credential.token')}</Label>
          <PasswordInput
            id="bearer-token"
            required
            value={bearerToken}
            onChange={(e) => setBearerToken(e.target.value)}
            className="mt-1"
          />
        </div>
      )}

      {(scheme === 'api_key_header' || scheme === 'api_key_query') && (
        <>
          <div>
            <Label htmlFor="api-key-name">{t('ingestion.credential.api_key.name')}</Label>
            <Input
              id="api-key-name"
              required
              value={apiKeyName}
              onChange={(e) => setApiKeyName(e.target.value)}
              className="mt-1"
            />
          </div>
          <div>
            <Label htmlFor="api-key-value">{t('ingestion.credential.api_key.value')}</Label>
            <PasswordInput
              id="api-key-value"
              required
              value={apiKeyValue}
              onChange={(e) => setApiKeyValue(e.target.value)}
              className="mt-1"
            />
          </div>
        </>
      )}

      {scheme === 'basic' && (
        <>
          <div>
            <Label htmlFor="basic-user">{t('ingestion.credential.basic.username')}</Label>
            <Input
              id="basic-user"
              required
              value={basicUsername}
              onChange={(e) => setBasicUsername(e.target.value)}
              className="mt-1"
            />
          </div>
          <div>
            <Label htmlFor="basic-pass">{t('ingestion.credential.basic.password')}</Label>
            <PasswordInput
              id="basic-pass"
              required
              value={basicPassword}
              onChange={(e) => setBasicPassword(e.target.value)}
              className="mt-1"
            />
          </div>
        </>
      )}

      <Button type="submit" disabled={submitting} variant="default">
        {t('ingestion.credential.create')}
      </Button>
    </form>
  )
}
