# AI 设置中心 · Part 2 · 前端实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 OpenCode Desktop v1.4.6 的侧边栏布局重写 `/settings`，落地数据源 / 提供商 / 模型三页；替换 `prompt-composer` 的硬编码模型选择器为真实数据驱动的 popover。

**Architecture:** 新增 `features/settings/` feature，内部按 section 分子目录（general / data-sources / providers / models）+ 顶层 `settings-layout.tsx` 壳。删除 `features/model-config/` 整包、`routes/__root.tsx` 内的 `SettingsDialog`。`prompt-composer` 下的模型选择器抽离到 `features/session/model-picker/`，数据通过 TanStack Query 从 `/api/ai/models` + `/api/ai/current-model` 拉取。

**Tech Stack:** React 19 / TanStack Router + Query / Zustand / shadcn/ui / vitest + @testing-library/react / MSW（可选，不强制）

**Spec 映射：** 实现 [2026-04-17-ai-settings-opencode-port.md](../../product-specs/2026-04-17-ai-settings-opencode-port.md) 的 §4（信息架构）、§7（前端结构）、§8（错误处理前端部分）、§9.2（前端测试）。

**依赖：** Part 1 已完成并合并到开发分支（`/api/ai/**` + `/api/connections/*` 全量可用）。

---

## 文件结构地图

**新增**
```
client/src/features/settings/
├── settings-layout.tsx           侧边栏 + 内容壳
├── settings-nav.tsx              左侧导航
├── shared/
│   ├── provider-icon.tsx         从 model-config 搬移
│   ├── recommended-providers.ts  "推荐"徽标白名单
│   └── api.ts                    fetch 函数集中 + TanStack Query keys
├── general/general-page.tsx      沿用 GeneralSettingsPanel
├── data-sources/
│   ├── data-sources-page.tsx
│   ├── connection-form-dialog.tsx
│   └── test-connection-button.tsx
├── providers/
│   ├── providers-page.tsx
│   └── connect-dialog.tsx        api-key 表单；oauth 类置灰
└── models/
    ├── models-page.tsx
    └── models-group.tsx

client/src/features/session/model-picker/
├── model-picker.tsx              [icon][name][▼] 触发器
└── model-picker-popover.tsx      分组列表 + 搜索
```

**修改**
- `client/src/routes/settings.tsx` → 指向新的 `SettingsLayout`，search params 改 `section`
- `client/src/routes/__root.tsx` → 删除 `<SettingsDialog />`
- `client/src/features/session/prompt-composer.tsx` → 替换 `<Select>` 为 `<ModelPicker />`，去掉 `MODELS` 常量

**删除**
- 整个 `client/src/features/model-config/` 目录

---

## Task 1：新增 shared API 层

**Files:**
- Create: `client/src/features/settings/shared/api.ts`
- Create: `client/src/features/settings/shared/recommended-providers.ts`

这一步先落地 fetch + query keys，后续页面依赖。

- [x] **Step 1：写失败测试**

```ts
// client/src/features/settings/shared/__tests__/api.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchProviders, fetchModels, patchModelEnabled,
         getCurrentModel, setCurrentModel, putCredentials } from '../api'

describe('settings api', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('fetchProviders calls /api/ai/providers', async () => {
    (globalThis.fetch as any).mockResolvedValue({
      ok: true, json: async () => ({ all: [], connected: [] })
    })
    await fetchProviders()
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/ai/providers')
  })

  it('patchModelEnabled posts to correct path', async () => {
    (globalThis.fetch as any).mockResolvedValue({ ok: true })
    await patchModelEnabled('openai', 'gpt-5', false)
    const [url, opts] = (globalThis.fetch as any).mock.calls[0]
    expect(url).toBe('/api/ai/models/openai/gpt-5')
    expect(opts.method).toBe('PATCH')
    expect(JSON.parse(opts.body)).toEqual({ enabled: false })
  })

  it('setCurrentModel patches /api/ai/current-model', async () => {
    (globalThis.fetch as any).mockResolvedValue({ ok: true })
    await setCurrentModel('openai/gpt-5')
    const [url, opts] = (globalThis.fetch as any).mock.calls[0]
    expect(url).toBe('/api/ai/current-model')
    expect(JSON.parse(opts.body)).toEqual({ modelId: 'openai/gpt-5' })
  })

  it('putCredentials forwards payload', async () => {
    (globalThis.fetch as any).mockResolvedValue({ ok: true })
    await putCredentials('openai', { type: 'api', key: 'sk-x' })
    const [url, opts] = (globalThis.fetch as any).mock.calls[0]
    expect(url).toBe('/api/ai/providers/openai/credentials')
    expect(opts.method).toBe('PUT')
  })
})
```

- [x] **Step 2：运行测试确认失败**

```
cd client && npx vitest run src/features/settings/shared/__tests__/api.test.ts
```
期望：模块不存在。

- [x] **Step 3：实现 api.ts**

```ts
// client/src/features/settings/shared/api.ts
export type ProviderDto = {
  id: string
  name: string
  connected: boolean
  models: { id: string; name: string; enabled: boolean }[]
}

export type ProvidersDto = { providers: ProviderDto[] }

export async function fetchProviders(): Promise<any> {
  const res = await fetch('/api/ai/providers')
  if (!res.ok) throw new Error(`fetchProviders ${res.status}`)
  return res.json()
}

export async function fetchProviderAuth(): Promise<Record<string, { type: string; label?: string }[]>> {
  const res = await fetch('/api/ai/providers/auth')
  if (!res.ok) throw new Error(`fetchProviderAuth ${res.status}`)
  return res.json()
}

export async function putCredentials(providerId: string, payload: unknown): Promise<void> {
  const res = await fetch(`/api/ai/providers/${providerId}/credentials`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) throw new Error(`putCredentials ${res.status}: ${await res.text()}`)
}

export async function fetchModels(): Promise<ProvidersDto> {
  const res = await fetch('/api/ai/models')
  if (!res.ok) throw new Error(`fetchModels ${res.status}`)
  return res.json()
}

export async function patchModelEnabled(providerId: string, modelId: string, enabled: boolean): Promise<void> {
  const res = await fetch(`/api/ai/models/${providerId}/${modelId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
  if (!res.ok) throw new Error(`patchModelEnabled ${res.status}`)
}

export async function getCurrentModel(): Promise<{ modelId: string | null }> {
  const res = await fetch('/api/ai/current-model')
  if (!res.ok) throw new Error(`getCurrentModel ${res.status}`)
  return res.json()
}

export async function setCurrentModel(modelId: string | null): Promise<void> {
  const res = await fetch('/api/ai/current-model', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ modelId }),
  })
  if (!res.ok) throw new Error(`setCurrentModel ${res.status}`)
}

export const aiQueryKeys = {
  providers: ['ai', 'providers'] as const,
  providerAuth: ['ai', 'providers', 'auth'] as const,
  models: ['ai', 'models'] as const,
  currentModel: ['ai', 'current-model'] as const,
}
```

- [x] **Step 4：实现 recommended-providers.ts**

```ts
// client/src/features/settings/shared/recommended-providers.ts
export const RECOMMENDED_PROVIDERS: ReadonlySet<string> = new Set([
  'opencode-zen',
  'opencode-go',
  'anthropic',
])

export const PROVIDER_DESCRIPTIONS: Record<string, string> = {
  'opencode-zen': '使用 OpenCode Zen 或 API 密钥连接',
  'opencode-go': '适合所有人的低成本订阅',
  'anthropic': '使用 Claude Pro/Max 或 API 密钥连接',
  'github-copilot': '使用 Copilot 或 API 密钥连接',
  'openai': '使用 ChatGPT Pro/Plus 或 API 密钥连接',
  'google': '使用 Gemini 或 API 密钥连接',
}
```

- [x] **Step 5：运行测试确认通过**

```
cd client && npx tsc --noEmit && npx vitest run src/features/settings/shared/__tests__/api.test.ts
```

- [x] **Step 6：提交**

```
git add client/src/features/settings/shared/
git commit -m "feat(settings): add shared api layer and recommended providers list"
```

---

## Task 2：侧边栏布局壳（settings-layout + settings-nav）

**Files:**
- Create: `client/src/features/settings/settings-layout.tsx`
- Create: `client/src/features/settings/settings-nav.tsx`

- [x] **Step 1：写组件测试**

```tsx
// client/src/features/settings/__tests__/settings-layout.test.tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { MemoryRouter } from '@tanstack/react-router' // 若项目无此导出，改用自己的 TestRouter 封装
import { SettingsLayout } from '../settings-layout'

// NOTE: 若 TanStack Router 测试桩较复杂，先手动构造路由 context：参考项目里
// 其它 route 的测试写法；没有则用 shallow render + 仅断言 nav 渲染。

describe('SettingsLayout', () => {
  it('renders all top-level nav entries', () => {
    render(<SettingsLayout><div>x</div></SettingsLayout>)
    expect(screen.getByText('通用')).toBeInTheDocument()
    expect(screen.getByText('数据源')).toBeInTheDocument()
    expect(screen.getByText('提供商')).toBeInTheDocument()
    expect(screen.getByText('模型')).toBeInTheDocument()
  })
})
```

- [x] **Step 2：运行测试确认失败**

```
cd client && npx vitest run src/features/settings/__tests__/settings-layout.test.tsx
```

- [x] **Step 3：实现 SettingsNav**

```tsx
// settings-nav.tsx
import { Link, useSearch } from '@tanstack/react-router'
import { cn } from '@/lib/utils'
import { SettingsIcon, DatabaseIcon, BoxIcon, SparklesIcon } from 'lucide-react'

type Section = 'general' | 'data-sources' | 'providers' | 'models'

const GROUPS: { title: string; items: { key: Section; label: string; icon: any }[] }[] = [
  { title: '桌面', items: [
    { key: 'general', label: '通用', icon: SettingsIcon },
  ]},
  { title: '服务器', items: [
    { key: 'data-sources', label: '数据源', icon: DatabaseIcon },
    { key: 'providers', label: '提供商', icon: BoxIcon },
    { key: 'models', label: '模型', icon: SparklesIcon },
  ]},
]

export function SettingsNav() {
  const search = useSearch({ from: '/settings' }) as { section?: Section }
  const current = search.section ?? 'general'
  return (
    <nav className="flex w-48 flex-col gap-4 border-r p-4 text-sm">
      {GROUPS.map(g => (
        <div key={g.title}>
          <div className="px-2 pb-1 text-xs text-muted-foreground">{g.title}</div>
          {g.items.map(it => {
            const Icon = it.icon
            return (
              <Link
                key={it.key}
                to="/settings"
                search={{ section: it.key }}
                className={cn(
                  'flex items-center gap-2 rounded px-2 py-1.5 hover:bg-accent',
                  current === it.key && 'bg-accent font-medium'
                )}
              >
                <Icon className="size-4" />{it.label}
              </Link>
            )
          })}
        </div>
      ))}
    </nav>
  )
}
```

- [x] **Step 4：实现 SettingsLayout**

```tsx
// settings-layout.tsx
import { useSearch } from '@tanstack/react-router'
import { SettingsNav } from './settings-nav'
import { GeneralPage } from './general/general-page'
import { DataSourcesPage } from './data-sources/data-sources-page'
import { ProvidersPage } from './providers/providers-page'
import { ModelsPage } from './models/models-page'

export function SettingsLayout() {
  const search = useSearch({ from: '/settings' }) as { section?: string }
  const section = search.section ?? 'general'
  return (
    <div className="flex h-screen">
      <SettingsNav />
      <main className="flex-1 overflow-y-auto p-6">
        {section === 'general' && <GeneralPage />}
        {section === 'data-sources' && <DataSourcesPage />}
        {section === 'providers' && <ProvidersPage />}
        {section === 'models' && <ModelsPage />}
      </main>
    </div>
  )
}
```

本步会因子页面尚未存在而编译失败，先创建最小 stub：

```tsx
// general/general-page.tsx
export function GeneralPage() { return <div>通用</div> }
// data-sources/data-sources-page.tsx
export function DataSourcesPage() { return <div>数据源</div> }
// providers/providers-page.tsx
export function ProvidersPage() { return <div>提供商</div> }
// models/models-page.tsx
export function ModelsPage() { return <div>模型</div> }
```

- [x] **Step 5：更新路由**

```ts
// client/src/routes/settings.tsx
import { createFileRoute } from '@tanstack/react-router'
import { SettingsLayout } from '@/features/settings/settings-layout'

export const Route = createFileRoute('/settings')({
  component: SettingsLayout,
  validateSearch: (search: Record<string, unknown>) => ({
    section: (search.section as string) ?? 'general',
  }),
})
```

- [x] **Step 6：运行测试 + 类型检查**

```
cd client && npx tsc --noEmit && npx vitest run src/features/settings/__tests__/settings-layout.test.tsx
```

- [x] **Step 7：提交**

```
git add client/src/features/settings/ client/src/routes/settings.tsx
git commit -m "feat(settings): add sidebar layout with nav and section stubs"
```

---

## Task 3：迁移 provider-icon 并复用 GeneralSettingsPanel

**Files:**
- Create: `client/src/features/settings/shared/provider-icon.tsx`
- Modify: `client/src/features/settings/general/general-page.tsx`

- [x] **Step 1：移动 provider-icon**

```
cp client/src/features/model-config/provider-icon.tsx \
   client/src/features/settings/shared/provider-icon.tsx
```

检查并调整 import（去掉相对路径依赖 mock-data 的部分，如有）。

- [x] **Step 2：GeneralPage 复用既有 `GeneralSettingsPanel`**

```tsx
// general/general-page.tsx
import { GeneralSettingsPanel } from '@/features/model-config/general-panel'

export function GeneralPage() {
  return (
    <div className="max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold">通用</h1>
      <GeneralSettingsPanel />
    </div>
  )
}
```

**NOTE：** Task 10 会从 `features/model-config/` 搬走 `general-panel.tsx` 到 `features/settings/general/` 内部文件。本步先复用旧路径，Task 10 统一清理。

- [x] **Step 3：类型检查**

```
cd client && npx tsc --noEmit
```

- [x] **Step 4：提交**

```
git add client/src/features/settings/shared/provider-icon.tsx \
        client/src/features/settings/general/general-page.tsx
git commit -m "feat(settings): wire general page to existing settings panel"
```

---

## Task 4：数据源页 — 列表 + 新增表单对话框

**Files:**
- Create: `client/src/features/settings/data-sources/data-sources-page.tsx`
- Create: `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- Create: `client/src/features/settings/data-sources/api.ts`

- [x] **Step 1：写 api 层**

```ts
// data-sources/api.ts
export type Connection = {
  id: string
  kind: 'mysql' | 'postgres' | 'h2'
  host: string
  port: number
  databaseName: string
  username: string
  createdAt: number
}

export async function listConnections(): Promise<Connection[]> {
  const res = await fetch('/api/connections')
  if (!res.ok) throw new Error(`listConnections ${res.status}`)
  const data = await res.json()
  return data.connections
}

export async function createConnection(body: {
  id: string; kind: string; host: string; port: number;
  database: string; username: string; password: string;
}): Promise<void> {
  const res = await fetch('/api/connections', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`create ${res.status}`)
}

export async function updateConnection(id: string, body: {
  kind: string; host: string; port: number;
  database: string; username: string; password: string | null;
}): Promise<void> {
  const res = await fetch(`/api/connections/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`update ${res.status}`)
}

export async function deleteConnection(id: string): Promise<void> {
  const res = await fetch(`/api/connections/${id}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`delete ${res.status}`)
}

export async function testConnection(id: string): Promise<{ ok: boolean; latencyMs: number; reason: string | null }> {
  const res = await fetch(`/api/connections/${id}/test`, { method: 'POST' })
  if (!res.ok) throw new Error(`test ${res.status}`)
  return res.json()
}

export const connectionsKey = ['connections'] as const
```

- [x] **Step 2：写页面组件**

```tsx
// data-sources-page.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { TrashIcon, PencilIcon, PlusIcon, CheckCircle2Icon, XCircleIcon } from 'lucide-react'
import { listConnections, deleteConnection, testConnection, connectionsKey, type Connection } from './api'
import { ConnectionFormDialog } from './connection-form-dialog'

export function DataSourcesPage() {
  const qc = useQueryClient()
  const { data: connections = [], isLoading } = useQuery({
    queryKey: connectionsKey, queryFn: listConnections,
  })
  const [editing, setEditing] = useState<Connection | null>(null)
  const [creating, setCreating] = useState(false)
  const [testResult, setTestResult] = useState<Record<string, 'ok' | 'fail' | 'loading'>>({})

  const del = useMutation({
    mutationFn: deleteConnection,
    onSuccess: () => { qc.invalidateQueries({ queryKey: connectionsKey }); toast.success('已删除') },
    onError: (e: Error) => toast.error(e.message),
  })

  async function runTest(id: string) {
    setTestResult(r => ({ ...r, [id]: 'loading' }))
    try {
      const r = await testConnection(id)
      setTestResult(s => ({ ...s, [id]: r.ok ? 'ok' : 'fail' }))
      toast[r.ok ? 'success' : 'error'](r.ok ? `连接成功 (${r.latencyMs}ms)` : r.reason ?? '失败')
    } catch (e) {
      setTestResult(s => ({ ...s, [id]: 'fail' }))
      toast.error((e as Error).message)
    }
  }

  return (
    <div className="max-w-4xl">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">数据源</h1>
        <Button onClick={() => setCreating(true)} size="sm">
          <PlusIcon className="size-4" /> 新增
        </Button>
      </div>

      {isLoading ? (
        <div className="text-sm text-muted-foreground">加载中...</div>
      ) : connections.length === 0 ? (
        <div className="rounded border border-dashed p-8 text-center text-sm text-muted-foreground">
          还没有数据源，点击"新增"创建第一个
        </div>
      ) : (
        <table className="w-full text-sm">
          <thead className="text-left text-muted-foreground">
            <tr><th className="pb-2">ID</th><th>类型</th><th>地址</th><th>数据库</th><th>用户</th><th></th></tr>
          </thead>
          <tbody>
            {connections.map((c) => (
              <tr key={c.id} className="border-t">
                <td className="py-2">{c.id}</td>
                <td>{c.kind}</td>
                <td>{c.host}:{c.port}</td>
                <td>{c.databaseName}</td>
                <td>{c.username}</td>
                <td className="text-right">
                  <Button size="sm" variant="ghost" onClick={() => runTest(c.id)}>
                    {testResult[c.id] === 'loading' ? '测试中…'
                      : testResult[c.id] === 'ok' ? <CheckCircle2Icon className="size-4 text-green-600" />
                      : testResult[c.id] === 'fail' ? <XCircleIcon className="size-4 text-red-600" />
                      : '测试'}
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setEditing(c)}><PencilIcon className="size-4" /></Button>
                  <Button size="sm" variant="ghost" onClick={() => del.mutate(c.id)}><TrashIcon className="size-4" /></Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <ConnectionFormDialog
        open={creating || !!editing}
        editing={editing}
        onClose={() => { setCreating(false); setEditing(null) }}
      />
    </div>
  )
}
```

- [x] **Step 3：写表单对话框**

```tsx
// connection-form-dialog.tsx
import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { createConnection, updateConnection, connectionsKey, type Connection } from './api'

const DEFAULTS: Record<string, { port: number }> = {
  mysql: { port: 3306 }, postgres: { port: 5432 }, h2: { port: 9092 },
}

type Props = { open: boolean; editing: Connection | null; onClose: () => void }

export function ConnectionFormDialog({ open, editing, onClose }: Props) {
  const qc = useQueryClient()
  const [form, setForm] = useState({
    id: '', kind: 'mysql', host: 'localhost', port: 3306,
    database: '', username: '', password: '',
  })

  useEffect(() => {
    if (editing) {
      setForm({
        id: editing.id, kind: editing.kind, host: editing.host,
        port: editing.port, database: editing.databaseName,
        username: editing.username, password: '',
      })
    } else {
      setForm({ id: '', kind: 'mysql', host: 'localhost', port: 3306,
        database: '', username: '', password: '' })
    }
  }, [editing, open])

  const save = useMutation({
    mutationFn: async () => {
      if (editing) {
        await updateConnection(editing.id, {
          kind: form.kind, host: form.host, port: form.port,
          database: form.database, username: form.username,
          password: form.password.length > 0 ? form.password : null,
        })
      } else {
        await createConnection(form)
      }
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: connectionsKey })
      toast.success(editing ? '已更新' : '已创建')
      onClose()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? '编辑数据源' : '新增数据源'}</DialogTitle>
        </DialogHeader>
        <div className="grid gap-3">
          <Field label="ID"><Input value={form.id} disabled={!!editing}
            onChange={(e) => setForm(f => ({ ...f, id: e.target.value }))} /></Field>
          <Field label="类型">
            <Select value={form.kind} onValueChange={(v) => setForm(f => ({ ...f, kind: v, port: DEFAULTS[v]?.port ?? f.port }))}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="mysql">MySQL</SelectItem>
                <SelectItem value="postgres">PostgreSQL</SelectItem>
                <SelectItem value="h2">H2</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field label="主机"><Input value={form.host}
            onChange={(e) => setForm(f => ({ ...f, host: e.target.value }))} /></Field>
          <Field label="端口"><Input type="number" value={form.port}
            onChange={(e) => setForm(f => ({ ...f, port: Number(e.target.value) }))} /></Field>
          <Field label="数据库"><Input value={form.database}
            onChange={(e) => setForm(f => ({ ...f, database: e.target.value }))} /></Field>
          <Field label="用户名"><Input value={form.username}
            onChange={(e) => setForm(f => ({ ...f, username: e.target.value }))} /></Field>
          <Field label={editing ? '密码（留空保持不变）' : '密码'}>
            <Input type="password" value={form.password}
              onChange={(e) => setForm(f => ({ ...f, password: e.target.value }))} />
          </Field>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>取消</Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {save.isPending ? '保存中…' : '保存'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (<div className="grid grid-cols-[120px_1fr] items-center gap-2">
    <Label>{label}</Label>{children}
  </div>)
}
```

- [x] **Step 4：类型检查 + 编译**

```
cd client && npx tsc --noEmit && npm run build --if-present
```

- [x] **Step 5：基础组件测试**

```tsx
// data-sources/__tests__/data-sources-page.test.tsx
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { DataSourcesPage } from '../data-sources-page'

describe('DataSourcesPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true, json: async () => ({ connections: [] })
    }))
  })
  it('shows empty state when no connections', async () => {
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><DataSourcesPage /></QueryClientProvider>)
    expect(await screen.findByText(/还没有数据源/)).toBeInTheDocument()
  })
})
```

```
cd client && npx vitest run src/features/settings/data-sources/__tests__/
```

- [x] **Step 6：提交**

```
git add client/src/features/settings/data-sources/
git commit -m "feat(settings): implement data sources page with CRUD and test connection"
```

---

## Task 5：提供商页 — 列表 + 连接对话框

**Files:**
- Create: `client/src/features/settings/providers/providers-page.tsx`
- Create: `client/src/features/settings/providers/connect-dialog.tsx`

- [x] **Step 1：写页面（连接 / 推荐两 section）**

```tsx
// providers-page.tsx
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { PlusIcon, RotateCwIcon } from 'lucide-react'
import { fetchProviders, aiQueryKeys } from '../shared/api'
import { ProviderIcon } from '../shared/provider-icon'
import { RECOMMENDED_PROVIDERS, PROVIDER_DESCRIPTIONS } from '../shared/recommended-providers'
import { ConnectDialog } from './connect-dialog'

type RawProvider = { id: string; name: string }

export function ProvidersPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: aiQueryKeys.providers, queryFn: fetchProviders,
  })
  const [editing, setEditing] = useState<{ id: string; name: string } | null>(null)

  const { connected, popular } = useMemo(() => {
    if (!data) return { connected: [], popular: [] }
    const connSet = new Set<string>(data.connected ?? [])
    const all: RawProvider[] = data.all ?? []
    return {
      connected: all.filter(p => connSet.has(p.id)),
      popular: all.filter(p => !connSet.has(p.id)),
    }
  }, [data])

  if (isLoading) return <div className="text-sm text-muted-foreground">加载中...</div>
  if (error) return (
    <div className="rounded border border-dashed p-8 text-center text-sm text-red-600">
      OpenCode 服务未连接，请检查后端 / OpenCode 进程后重试
    </div>
  )

  return (
    <div className="max-w-3xl">
      <h1 className="mb-6 text-2xl font-semibold">提供商</h1>

      <Section title="已连接的提供商" empty="没有已连接的提供商">
        {connected.map(p => <Row key={p.id} p={p} action="reconfigure" onClick={() => setEditing(p)} />)}
      </Section>

      <Section title="热门提供商">
        {popular.map(p => <Row key={p.id} p={p} action="connect" onClick={() => setEditing(p)} />)}
      </Section>

      <ConnectDialog providerId={editing?.id ?? null} providerName={editing?.name ?? ''}
        onClose={() => setEditing(null)} />
    </div>
  )
}

function Section({ title, empty, children }: { title: string; empty?: string; children: React.ReactNode }) {
  const isEmpty = !Array.isArray(children) || (children as any[]).length === 0
  return (
    <section className="mb-8">
      <h2 className="mb-3 text-sm font-medium">{title}</h2>
      {isEmpty && empty ? (
        <div className="rounded border border-dashed p-6 text-center text-sm text-muted-foreground">
          {empty}
        </div>
      ) : <div className="divide-y rounded border">{children}</div>}
    </section>
  )
}

function Row({ p, action, onClick }: { p: RawProvider; action: 'connect' | 'reconfigure'; onClick: () => void }) {
  const recommended = RECOMMENDED_PROVIDERS.has(p.id)
  const desc = PROVIDER_DESCRIPTIONS[p.id] ?? '使用 API 密钥连接'
  return (
    <div className="flex items-center gap-3 p-3">
      <ProviderIcon id={p.id} className="size-6" />
      <div className="flex-1">
        <div className="flex items-center gap-2 text-sm font-medium">
          {p.name}
          {recommended && <span className="rounded bg-accent px-1.5 py-0.5 text-xs">推荐</span>}
        </div>
        <div className="text-xs text-muted-foreground">{desc}</div>
      </div>
      <Button size="sm" variant="outline" onClick={onClick}>
        {action === 'connect' ? <><PlusIcon className="size-4" />连接</>
                               : <><RotateCwIcon className="size-4" />重新配置</>}
      </Button>
    </div>
  )
}
```

- [x] **Step 2：写连接对话框（仅 api 类型；oauth 置灰）**

```tsx
// connect-dialog.tsx
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
    queryKey: aiQueryKeys.providerAuth, queryFn: fetchProviderAuth,
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
```

- [x] **Step 3：基础测试**

```tsx
// providers/__tests__/providers-page.test.tsx
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ProvidersPage } from '../providers-page'

function wrap(body: unknown) {
  return { ok: true, json: async () => body }
}

describe('ProvidersPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wrap({
      all: [
        { id: 'openai', name: 'OpenAI' },
        { id: 'anthropic', name: 'Anthropic' }
      ],
      connected: ['openai']
    })))
  })
  it('separates connected and popular', async () => {
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><ProvidersPage /></QueryClientProvider>)
    expect(await screen.findByText('已连接的提供商')).toBeInTheDocument()
    expect(screen.getByText('OpenAI')).toBeInTheDocument()
    expect(screen.getByText('Anthropic')).toBeInTheDocument()
    expect(screen.getAllByText(/推荐/).length).toBeGreaterThan(0) // anthropic 在推荐名单
  })
})
```

```
cd client && npx vitest run src/features/settings/providers/__tests__/
```

- [x] **Step 4：类型检查 + 提交**

```
cd client && npx tsc --noEmit
git add client/src/features/settings/providers/
git commit -m "feat(settings): implement providers page with connect dialog"
```

---

## Task 6：模型页 — 搜索 + 分组 + 启禁 switch

**Files:**
- Create: `client/src/features/settings/models/models-page.tsx`
- Create: `client/src/features/settings/models/models-group.tsx`

- [x] **Step 1：页面组件**

```tsx
// models-page.tsx
import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Input } from '@/components/ui/input'
import { SearchIcon } from 'lucide-react'
import { fetchModels, aiQueryKeys } from '../shared/api'
import { ModelsGroup } from './models-group'

export function ModelsPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: aiQueryKeys.models, queryFn: fetchModels,
  })
  const [q, setQ] = useState('')

  const filtered = useMemo(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    return data.providers
      .filter(p => p.connected)
      .map(p => ({ ...p, models: needle
        ? p.models.filter(m => m.name.toLowerCase().includes(needle) || m.id.toLowerCase().includes(needle))
        : p.models }))
      .filter(p => p.models.length > 0)
  }, [data, q])

  if (isLoading) return <div className="text-sm text-muted-foreground">加载中...</div>
  if (error) return <div className="rounded border border-dashed p-8 text-center text-sm text-red-600">
    OpenCode 服务未连接
  </div>

  return (
    <div className="max-w-3xl">
      <h1 className="mb-6 text-2xl font-semibold">模型</h1>
      <div className="relative mb-4">
        <SearchIcon className="absolute left-2 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input className="pl-8" placeholder="搜索模型"
          value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      {filtered.length === 0 ? (
        <div className="rounded border border-dashed p-8 text-center text-sm text-muted-foreground">
          {q ? '没有匹配的模型' : '尚未连接任何提供商'}
        </div>
      ) : filtered.map(p => <ModelsGroup key={p.id} provider={p} />)}
    </div>
  )
}
```

- [x] **Step 2：分组组件**

```tsx
// models-group.tsx
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Switch } from '@/components/ui/switch'
import { ProviderIcon } from '../shared/provider-icon'
import { patchModelEnabled, aiQueryKeys, type ProviderDto } from '../shared/api'

export function ModelsGroup({ provider }: { provider: ProviderDto }) {
  const qc = useQueryClient()
  const toggle = useMutation({
    mutationFn: (v: { modelId: string; enabled: boolean }) =>
      patchModelEnabled(provider.id, v.modelId, v.enabled),
    onSuccess: () => qc.invalidateQueries({ queryKey: aiQueryKeys.models }),
  })
  return (
    <div className="mb-6">
      <div className="mb-2 flex items-center gap-2 text-sm font-medium">
        <ProviderIcon id={provider.id} className="size-4" />{provider.name}
      </div>
      <div className="divide-y rounded border">
        {provider.models.map(m => (
          <div key={m.id} className="flex items-center justify-between p-3">
            <span className="text-sm">{m.name}</span>
            <Switch checked={m.enabled}
              onCheckedChange={(v) => toggle.mutate({ modelId: m.id, enabled: v })} />
          </div>
        ))}
      </div>
    </div>
  )
}
```

- [x] **Step 3：基础测试**

```tsx
// models/__tests__/models-page.test.tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ModelsPage } from '../models-page'

describe('ModelsPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        providers: [{
          id: 'openai', name: 'OpenAI', connected: true,
          models: [
            { id: 'gpt-5', name: 'GPT-5', enabled: true },
            { id: 'gpt-5-nano', name: 'GPT-5 Nano', enabled: false },
          ]
        }]
      })
    }))
  })

  it('renders connected provider group and filters by search', async () => {
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><ModelsPage /></QueryClientProvider>)
    expect(await screen.findByText('GPT-5')).toBeInTheDocument()
    fireEvent.change(screen.getByPlaceholderText('搜索模型'), { target: { value: 'nano' } })
    await waitFor(() => expect(screen.queryByText('GPT-5')).not.toBeInTheDocument())
    expect(screen.getByText('GPT-5 Nano')).toBeInTheDocument()
  })
})
```

```
cd client && npx vitest run src/features/settings/models/__tests__/
```

- [x] **Step 4：类型检查 + 提交**

```
cd client && npx tsc --noEmit
git add client/src/features/settings/models/
git commit -m "feat(settings): implement models page with search and enable toggle"
```

---

## Task 7：Chat composer 模型选择器（紧凑触发器 + Popover）

**Files:**
- Create: `client/src/features/session/model-picker/model-picker.tsx`
- Create: `client/src/features/session/model-picker/model-picker-popover.tsx`
- Modify: `client/src/features/session/prompt-composer.tsx`

- [x] **Step 1：写触发器 + Popover（分组 + 搜索）**

```tsx
// model-picker.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronDownIcon } from 'lucide-react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { ProviderIcon } from '@/features/settings/shared/provider-icon'
import { aiQueryKeys, fetchModels, getCurrentModel, setCurrentModel } from '@/features/settings/shared/api'
import { ModelPickerPopover } from './model-picker-popover'

export function ModelPicker() {
  const [open, setOpen] = useState(false)
  const qc = useQueryClient()
  const { data: models } = useQuery({ queryKey: aiQueryKeys.models, queryFn: fetchModels })
  const { data: current } = useQuery({ queryKey: aiQueryKeys.currentModel, queryFn: getCurrentModel })
  const mut = useMutation({
    mutationFn: (id: string | null) => setCurrentModel(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: aiQueryKeys.currentModel }),
  })

  const selected = resolveSelected(models?.providers ?? [], current?.modelId ?? null)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button type="button"
          className="flex h-7 items-center gap-1.5 rounded px-2 text-xs hover:bg-accent/50">
          {selected ? (
            <>
              <ProviderIcon id={selected.providerId} className="size-3.5" />
              <span className="max-w-[140px] truncate">{selected.modelName}</span>
            </>
          ) : (
            <span className="text-muted-foreground">选择模型</span>
          )}
          <ChevronDownIcon className="size-3" />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="p-0">
        <ModelPickerPopover
          providers={models?.providers ?? []}
          currentModelId={current?.modelId ?? null}
          onPick={(id) => { mut.mutate(id); setOpen(false) }}
        />
      </PopoverContent>
    </Popover>
  )
}

function resolveSelected(providers: { id: string; connected: boolean; models: { id: string; name: string; enabled: boolean }[] }[], modelId: string | null) {
  if (!modelId) return null
  const [providerId, ...rest] = modelId.split('/')
  const mid = rest.join('/')
  const p = providers.find(x => x.id === providerId && x.connected)
  const m = p?.models.find(x => x.id === mid && x.enabled)
  return p && m ? { providerId: p.id, modelName: m.name } : null
}
```

```tsx
// model-picker-popover.tsx
import { useMemo, useState } from 'react'
import { Input } from '@/components/ui/input'
import { ProviderIcon } from '@/features/settings/shared/provider-icon'
import type { ProviderDto } from '@/features/settings/shared/api'
import { cn } from '@/lib/utils'

type Props = {
  providers: ProviderDto[]
  currentModelId: string | null
  onPick: (modelId: string) => void
}

export function ModelPickerPopover({ providers, currentModelId, onPick }: Props) {
  const [q, setQ] = useState('')
  const groups = useMemo(() => {
    const needle = q.trim().toLowerCase()
    return providers.filter(p => p.connected)
      .map(p => ({
        ...p,
        models: p.models
          .filter(m => m.enabled)
          .filter(m => !needle || m.name.toLowerCase().includes(needle) || m.id.toLowerCase().includes(needle))
      }))
      .filter(p => p.models.length > 0)
  }, [providers, q])

  return (
    <div className="w-[280px]">
      <div className="border-b p-2">
        <Input className="h-7" placeholder="搜索模型"
          value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      <div className="max-h-[320px] overflow-y-auto p-1">
        {groups.length === 0 && (
          <div className="p-3 text-center text-xs text-muted-foreground">
            {providers.length === 0 ? '请先在设置中配置提供商' : '没有匹配的模型'}
          </div>
        )}
        {groups.map(p => (
          <div key={p.id} className="mb-2">
            <div className="flex items-center gap-1.5 px-2 py-1 text-xs text-muted-foreground">
              <ProviderIcon id={p.id} className="size-3.5" />{p.name}
            </div>
            {p.models.map(m => {
              const id = `${p.id}/${m.id}`
              const active = id === currentModelId
              return (
                <button key={id} type="button" onClick={() => onPick(id)}
                  className={cn(
                    'block w-full rounded px-2 py-1 text-left text-sm hover:bg-accent',
                    active && 'bg-accent'
                  )}>{m.name}</button>
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
}
```

- [x] **Step 2：替换 prompt-composer 内的模型选择器**

修改 `client/src/features/session/prompt-composer.tsx`：

1. 删除顶部 `const MODELS = ...` 和 `Select*` 相关 import
2. 删除 `const [selectedModel, setSelectedModel] = ...`
3. 在 import 区域加：`import { ModelPicker } from './model-picker/model-picker'`
4. 把 `<Select value={selectedModel}...>...</Select>` 整段替换为：`<ModelPicker />`

结果：composer 不再持有 `selectedModel` 状态；所有模型选择都通过 React Query `getCurrentModel / setCurrentModel` 与后端同步。

- [x] **Step 3：vitest 基础测试**

```tsx
// model-picker/__tests__/model-picker.test.tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ModelPicker } from '../model-picker'

describe('ModelPicker', () => {
  beforeEach(() => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/ai/models') return Promise.resolve({ ok: true, json: async () => ({
        providers: [{ id: 'openai', name: 'OpenAI', connected: true,
          models: [{ id: 'gpt-5', name: 'GPT-5', enabled: true }] }] }) })
      if (url === '/api/ai/current-model') return Promise.resolve({ ok: true, json: async () => ({ modelId: null }) })
      return Promise.resolve({ ok: true, json: async () => ({}) })
    })
    vi.stubGlobal('fetch', fetchMock)
  })
  it('shows placeholder when nothing selected, opens popover on click', async () => {
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><ModelPicker /></QueryClientProvider>)
    expect(await screen.findByText('选择模型')).toBeInTheDocument()
    fireEvent.click(screen.getByText('选择模型'))
    await waitFor(() => expect(screen.getByText('GPT-5')).toBeInTheDocument())
  })
})
```

```
cd client && npx vitest run src/features/session/model-picker/__tests__/
```

- [x] **Step 4：类型检查**

```
cd client && npx tsc --noEmit
```

- [x] **Step 5：提交**

```
git add client/src/features/session/model-picker/ \
        client/src/features/session/prompt-composer.tsx
git commit -m "feat(session): replace hard-coded model select with data-driven picker"
```

---

## Task 8：删除 __root.tsx 的 SettingsDialog + 整个 features/model-config

**Files:**
- Modify: `client/src/routes/__root.tsx`
- Delete: `client/src/features/model-config/**`
- Modify: `client/src/features/settings/general/general-page.tsx`（把 `GeneralSettingsPanel` 搬进来）

- [x] **Step 1：搬 `GeneralSettingsPanel` 源码到 settings/general/**

```bash
cp client/src/features/model-config/general-panel.tsx \
   client/src/features/settings/general/general-panel.tsx
```

修改 `settings/general/general-page.tsx` 的 import：

```tsx
import { GeneralSettingsPanel } from './general-panel'
```

类型检查通过后再删旧文件。

- [x] **Step 2：移除 `<SettingsDialog />`**

```tsx
// __root.tsx
import { createRootRoute, Outlet } from '@tanstack/react-router'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'

export const Route = createRootRoute({
  component: RootComponent,
})

function RootComponent() {
  return (
    <TooltipProvider>
      <Outlet />
      <Toaster richColors position="top-right" />
    </TooltipProvider>
  )
}
```

搜索引用 `model-config` 的地方：

```
cd client && grep -r "features/model-config" src/
```

凡是引用都改到 `features/settings/` 下新位置，或直接删除（如 MOCK 数据）。

- [x] **Step 3：删除旧目录**

```
rm -rf client/src/features/model-config/
```

- [x] **Step 4：类型检查 + 全量测试**

```
cd client && npx tsc --noEmit && npx vitest run
```
期望：0 type error，所有测试 PASS。

- [x] **Step 5：提交**

```
git add -A client/src/features/ client/src/routes/__root.tsx
git commit -m "refactor(client): remove legacy model-config and settings dialog"
```

---

## Task 9：端到端 smoke（人工）+ 计划索引收尾

**Files:**
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1：前端 + 后端同时起**

```
# Terminal 1
cd server && mvn spring-boot:run -pl data-talk-adapter
# Terminal 2
cd client && npm run tauri dev
# Terminal 3（确保 OpenCode 本地跑在 :4096）
```

- [x] **Step 2：手动 smoke 走位**

- 打开 `/settings` → 侧边栏四项都在（通用 / 数据源 / 提供商 / 模型）
- 数据源：新增一条 MySQL → 测试连接（预期失败，正常 → 新增 H2 in-memory 测试）→ 编辑 → 删除
- 提供商：切换到 Providers → 选一个 api 类（如 OpenAI）→ 填 API Key → 保存
- 模型：刚连接的 provider 下能看到模型 → 关掉其中一个
- 回到 chat 页面 → prompt composer 模型选择器 `[icon][name][▼]` 展示正确 → 点开 Popover 能看到启用的模型 → 选中一个
- 在 chat 发送一条 "你好" → OpenCode 日志能看到 body 里带 `model` 字段

- [x] **Step 3：登记计划完成**

修改 `docs/exec-plans/index.md`：将 Part 1 + Part 2 从活跃移到已完成，附完成日期。

- [x] **Step 4：提交**

```
git add docs/exec-plans/index.md
git commit -m "docs(plan): mark AI settings part 1/2 as completed"
```

---

## 执行顺序备注

1. Task 1（shared api）→ Task 2（layout）→ Task 3（general 接线）
2. Task 4（数据源）可与 Task 5、Task 6 并行（彼此独立）
3. Task 7 依赖 Task 1（shared api）和 Task 6（模型页已用过 `aiQueryKeys.models`）
4. Task 8 放最后，等所有新页面跑通再删旧代码
5. Task 9 收尾

---

## 决策日志

- **why Popover not Select**：shadcn `<Select>` 语义上选一个值，但模型选择需要分组 + 搜索 + 按 provider 聚合，Popover + 自绘列表更贴合交互
- **why `setCurrentModel` 不做乐观更新**：composer 显示当前模型靠后端真相；乐观更新一旦 PATCH 失败需要回滚，复杂度不值
- **why 把 `GeneralSettingsPanel` 移动而非 re-export**：避免 `features/settings/` 还残留对旧 `features/model-config/` 的依赖路径，清理要一次做干净
- **why provider-icon 放 shared**：模型选择器（`features/session/`）和设置页都要用，放 `features/settings/shared/` 是 settings 为主、session 只是消费者
