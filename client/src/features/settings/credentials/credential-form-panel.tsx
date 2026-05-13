import { useEffect, useState } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PasswordInput } from '@/components/ui/password-input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { toast } from 'sonner'
import { useCreateCredentialMutation, useUpdateCredentialMutation } from './hooks/use-credentials-query'
import type { AuthScheme, CredentialView } from './api/credential-api'

const SCHEMES: { value: AuthScheme; labelKey: string }[] = [
  { value: 'none', labelKey: 'ingestion.credential.scheme.none' },
  { value: 'bearer', labelKey: 'ingestion.credential.scheme.bearer' },
  { value: 'api_key_header', labelKey: 'ingestion.credential.scheme.api_key_header' },
  { value: 'api_key_query', labelKey: 'ingestion.credential.scheme.api_key_query' },
  { value: 'basic', labelKey: 'ingestion.credential.scheme.basic' },
]

interface Props {
  editing: CredentialView | null
  onCancel: () => void
  onSaved: () => void
}

export function CredentialFormPanel({ editing, onCancel, onSaved }: Props) {
  const { t } = useI18n()
  const createMut = useCreateCredentialMutation()
  const updateMut = useUpdateCredentialMutation()
  const isPending = createMut.isPending || updateMut.isPending

  const [name, setName] = useState('')
  const [scheme, setScheme] = useState<AuthScheme>('none')
  const [bearerToken, setBearerToken] = useState('')
  const [apiKeyName, setApiKeyName] = useState('')
  const [apiKeyValue, setApiKeyValue] = useState('')
  const [basicUsername, setBasicUsername] = useState('')
  const [basicPassword, setBasicPassword] = useState('')

  useEffect(() => {
    if (editing) {
      setName(editing.name)
      setScheme(editing.authScheme)
      setBearerToken('')
      setApiKeyName(editing.configNonSecret.headerName ?? editing.configNonSecret.queryName ?? '')
      setApiKeyValue('')
      setBasicUsername(editing.configNonSecret.username ?? '')
      setBasicPassword('')
    } else {
      setName('')
      setScheme('none')
      setBearerToken('')
      setApiKeyName('')
      setApiKeyValue('')
      setBasicUsername('')
      setBasicPassword('')
    }
  }, [editing])

  function handleSave() {
    const trimmedName = name.trim()
    if (!trimmedName) return

    let configNonSecret: Record<string, string> = {}
    let secret: string | null = null

    if (scheme === 'bearer') {
      secret = bearerToken || null
    } else if (scheme === 'api_key_header') {
      configNonSecret = { headerName: apiKeyName }
      secret = apiKeyValue || null
    } else if (scheme === 'api_key_query') {
      configNonSecret = { queryName: apiKeyName }
      secret = apiKeyValue || null
    } else if (scheme === 'basic') {
      configNonSecret = { username: basicUsername }
      secret = basicPassword || null
    }

    const payload = { name: trimmedName, authScheme: scheme, configNonSecret, secret }

    if (editing) {
      updateMut.mutate(
        { id: editing.id, req: payload },
        {
          onSuccess: () => {
            toast.success(t('common.done'))
            onSaved()
          },
          onError: (err) => {
            toast.error(err instanceof Error ? err.message : t('common.unknownError'))
          },
        },
      )
    } else {
      createMut.mutate(payload, {
        onSuccess: () => {
          toast.success(t('common.done'))
          onSaved()
        },
        onError: (err) => {
          toast.error(err instanceof Error ? err.message : t('common.unknownError'))
        },
      })
    }
  }

  return (
    <div className="rounded-lg border bg-card p-6">
      <h2 className="mb-4 text-lg font-medium">
        {editing ? t('ingestion.credential.edit') : t('ingestion.credential.create')}
      </h2>
      <div className="grid gap-4">
        <Field label={t('ingestion.credential.name')}>
          <Input
            aria-label={t('ingestion.credential.name')}
            data-testid="credential-name-input"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </Field>
        <Field label={t('ingestion.credential.scheme.label')}>
          <Select value={scheme} onValueChange={(v) => setScheme(v as AuthScheme)}>
            <SelectTrigger aria-label={t('ingestion.credential.scheme.label')} data-testid="credential-scheme-select">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {SCHEMES.map((s) => (
                <SelectItem key={s.value} value={s.value}>
                  {t(s.labelKey as any)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>

        {scheme === 'bearer' && (
          <Field label={t('ingestion.credential.token')}>
            <PasswordInput
              id="bearer-token"
              required={!editing}
              placeholder={editing ? t('ingestion.credential.keepSecret') : undefined}
              value={bearerToken}
              onChange={(e) => setBearerToken(e.target.value)}
            />
          </Field>
        )}

        {(scheme === 'api_key_header' || scheme === 'api_key_query') && (
          <>
            <Field label={t('ingestion.credential.api_key.name')}>
              <Input
                id="api-key-name"
                required
                value={apiKeyName}
                onChange={(e) => setApiKeyName(e.target.value)}
              />
            </Field>
            <Field label={t('ingestion.credential.api_key.value')}>
              <PasswordInput
                id="api-key-value"
                required={!editing}
                placeholder={editing ? t('ingestion.credential.keepSecret') : undefined}
                value={apiKeyValue}
                onChange={(e) => setApiKeyValue(e.target.value)}
              />
            </Field>
          </>
        )}

        {scheme === 'basic' && (
          <>
            <Field label={t('ingestion.credential.basic.username')}>
              <Input
                id="basic-user"
                required
                value={basicUsername}
                onChange={(e) => setBasicUsername(e.target.value)}
              />
            </Field>
            <Field label={t('ingestion.credential.basic.password')}>
              <PasswordInput
                id="basic-pass"
                required={!editing}
                placeholder={editing ? t('ingestion.credential.keepSecret') : undefined}
                value={basicPassword}
                onChange={(e) => setBasicPassword(e.target.value)}
              />
            </Field>
          </>
        )}
      </div>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel} disabled={isPending}>
          {t('common.cancel')}
        </Button>
        <Button onClick={handleSave} disabled={isPending}>
          {isPending ? t('common.saving') : (editing ? t('common.save') : t('ingestion.credential.create'))}
        </Button>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[120px_1fr] items-center gap-2">
      <Label>{label}</Label>
      {children}
    </div>
  )
}
