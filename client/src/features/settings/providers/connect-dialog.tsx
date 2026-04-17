import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { fetchProviderAuth, putCredentials, aiQueryKeys } from '../shared/api'

type Props = { providerId: string | null; providerName: string; onClose: () => void }

export function ConnectDialog({ providerId, providerName, onClose }: Props) {
  const qc = useQueryClient()
  const { data: auth } = useQuery({
    queryKey: aiQueryKeys.providerAuth,
    queryFn: fetchProviderAuth,
    enabled: !!providerId,
  })
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('')

  useEffect(() => { setApiKey(''); setBaseUrl('') }, [providerId])

  const methods = providerId ? (auth?.[providerId] ?? []) : []
  const hasApi = methods.some(m => m.type === 'api')
  const hasOauth = methods.some(m => m.type === 'oauth')

  const save = useMutation({
    mutationFn: async () => {
      if (!providerId) return
      const payload: any = { type: 'api', key: apiKey }
      if (baseUrl.trim()) payload.baseURL = baseUrl.trim()
      await putCredentials(providerId, payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: aiQueryKeys.providers })
      qc.invalidateQueries({ queryKey: aiQueryKeys.models })
      toast.success('已保存凭证')
      onClose()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <Dialog open={!!providerId} onOpenChange={(v) => !v && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>连接 {providerName}</DialogTitle></DialogHeader>

        {hasOauth && !hasApi && (
          <div className="rounded border border-dashed p-4 text-sm text-muted-foreground">
            此提供商仅支持 OAuth 登录。请在终端运行：
            <pre className="mt-2 rounded bg-muted p-2 text-xs">opencode auth login {providerId}</pre>
          </div>
        )}

        {hasApi && (
          <div className="grid gap-3">
            <div>
              <Label>API Key</Label>
              <Input type="password" value={apiKey}
                onChange={(e) => setApiKey(e.target.value)} autoFocus />
            </div>
            <div>
              <Label>Base URL（可选）</Label>
              <Input value={baseUrl}
                placeholder="自定义或兼容网关地址"
                onChange={(e) => setBaseUrl(e.target.value)} />
            </div>
            {hasOauth && (
              <p className="text-xs text-muted-foreground">
                如需使用 OAuth 登录，请在 CLI 运行 <code>opencode auth login {providerId}</code>
              </p>
            )}
            <p className="text-xs text-muted-foreground">
              注：凭证存储在 OpenCode（<code>~/.local/share/opencode/auth.json</code>）。
              如需移除请编辑该文件或使用 opencode CLI。
            </p>
          </div>
        )}

        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>取消</Button>
          <Button onClick={() => save.mutate()}
            disabled={!hasApi || apiKey.length === 0 || save.isPending}>
            {save.isPending ? '保存中…' : '保存'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
