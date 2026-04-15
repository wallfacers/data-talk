# Frontend Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全面重构前端项目为 Feature-based 分层架构，所有 UI 组件使用 shadcn/ui，删除所有旧组件，建立 chat、query-result、sidebar 三大功能模块。

**Architecture:** Feature-based 分层架构（app / components / features / hooks / lib / services / types），状态提升 + hooks 封装，组件不直接调用 API。

**Tech Stack:** React 19 + TypeScript + Vite + shadcn/ui + TailwindCSS + Lucide Icons + Tauri 2

---

## 文件变更清单

| 操作 | 文件 | 职责 |
|------|------|------|
| **Delete** | `client/src/components/ChatArea.tsx` | 旧聊天组件 |
| **Delete** | `client/src/components/MessageBubble.tsx` | 旧消息组件 |
| **Delete** | `client/src/components/QueryResult.tsx` | 旧查询结果组件 |
| **Modify** | `client/src/App.tsx` | 重写为新架构入口 |
| **Modify** | `client/src/main.tsx` | 更新 import 路径 |
| **Create** | `client/src/types/index.ts` | 全局类型定义 |
| **Create** | `client/src/app/layout.tsx` | 全局布局组件 |
| **Create** | `client/src/features/chat/types.ts` | 聊天类型 |
| **Create** | `client/src/features/chat/hooks/use-chat.ts` | 聊天逻辑 hook |
| **Create** | `client/src/features/chat/components/chat-input.tsx` | 聊天输入组件 |
| **Create** | `client/src/features/chat/components/message-item.tsx` | 消息气泡组件 |
| **Create** | `client/src/features/chat/components/chat-area.tsx` | 聊天容器组件 |
| **Create** | `client/src/features/query-result/types.ts` | 查询结果类型 |
| **Create** | `client/src/features/query-result/components/result-table.tsx` | 结果表格 |
| **Create** | `client/src/features/query-result/components/query-result.tsx` | 查询结果容器 |
| **Create** | `client/src/features/sidebar/components/app-sidebar.tsx` | 侧边栏主组件 |
| **Create** | `client/src/features/sidebar/components/nav-main.tsx` | 侧边栏主导航 |
| **Create** | `client/src/features/sidebar/components/nav-projects.tsx` | 侧边栏项目 |
| **Create** | `client/src/features/sidebar/components/nav-user.tsx` | 侧边栏用户 |
| **Create** | `client/src/features/sidebar/components/team-switcher.tsx` | 侧边栏团队切换 |

---

## Task 1: 安装 shadcn 组件

**Files:**
- Modify: `client/components.json` (shadcn 配置)
- Create: `client/src/components/ui/card.tsx`
- Create: `client/src/components/ui/badge.tsx`
- Create: `client/src/components/ui/scroll-area.tsx`
- Create: `client/src/components/ui/alert.tsx`

- [ ] **Step 1: 安装新 shadcn 组件**

在 `client/` 目录下运行：

```bash
cd client
npx shadcn@latest add card
npx shadcn@latest add badge
npx shadcn@latest add scroll-area
npx shadcn@latest add alert
```

- [ ] **Step 2: 安装社区组件 InputGroup 和 AutosizeTextarea**

```bash
cd client
npx shadcn@latest add https://originui.com/input-group
npx shadcn@latest add https://originui.com/autosize-textarea
```

> 如果 originui.com registry 安装失败，改为手动创建这两个组件（见下方代码）。

- [ ] **Step 3: 手动创建 InputGroup（如果社区安装失败）**

如果 Step 2 安装失败，手动创建 `client/src/components/input-group.tsx`：

```tsx
import * as React from "react"
import { cn } from "@/lib/utils"

function InputGroup({
  className,
  children,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      className={cn(
        "flex w-full items-center rounded-xl border border-input bg-background shadow-sm transition-colors focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/20",
        className
      )}
      {...props}
    >
      {children}
    </div>
  )
}

export { InputGroup }
```

- [ ] **Step 4: 手动创建 AutosizeTextarea（如果社区安装失败）**

如果 Step 2 安装失败，手动创建 `client/src/components/autosize-textarea.tsx`：

```tsx
import * as React from "react"
import { cn } from "@/lib/utils"

interface AutosizeTextareaProps
  extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  minRows?: number
  maxRows?: number
}

const AutosizeTextarea = React.forwardRef<
  HTMLTextAreaElement,
  AutosizeTextareaProps
>(({ className, minRows = 1, maxRows = 5, onChange, ...props }, ref) => {
  const textareaRef = React.useRef<HTMLTextAreaElement>(null)

  const resizeParent = React.useCallback((textarea: HTMLTextAreaElement) => {
    textarea.style.height = "auto"
    textarea.style.height = textarea.scrollHeight + "px"
  }, [])

  React.useEffect(() => {
    const textarea = textareaRef.current
    if (!textarea) return
    resizeParent(textarea)
  }, [props.value, resizeParent])

  const handleChange = React.useCallback(
    (event: React.ChangeEvent<HTMLTextAreaElement>) => {
      onChange?.(event)
      resizeParent(event.target)
    },
    [onChange, resizeParent]
  )

  return (
    <textarea
      ref={(node) => {
        textareaRef.current = node
        if (typeof ref === "function") ref(node)
        else if (ref) ref.current = node
      }}
      onChange={handleChange}
      className={cn(
        "flex w-full resize-none bg-transparent px-3 py-2 text-base text-foreground placeholder:text-muted-foreground focus:outline-none md:text-sm",
        className
      )}
      rows={minRows}
      {...props}
    />
  )
})
AutosizeTextarea.displayName = "AutosizeTextarea"

export { AutosizeTextarea }
```

- [ ] **Step 5: 验证所有组件已安装**

```bash
ls client/src/components/ui/
# 应包含: card.tsx badge.tsx scroll-area.tsx alert.tsx
ls client/src/components/input-group.tsx client/src/components/autosize-textarea.tsx 2>/dev/null
# 应能找到这两个文件
```

- [ ] **Step 6: 提交**

```bash
cd client
git add src/components/ui/card.tsx src/components/ui/badge.tsx src/components/ui/scroll-area.tsx src/components/ui/alert.tsx src/components/input-group.tsx src/components/autosize-textarea.tsx
git commit -m "chore: install shadcn components for refactor (card, badge, scroll-area, alert, input-group, autosize-textarea)"
```

---

## Task 2: 创建目录结构 + 类型定义 + 删除旧组件

**Files:**
- Create: `client/src/types/index.ts`
- Create: `client/src/features/chat/types.ts`
- Create: `client/src/features/query-result/types.ts`
- Delete: `client/src/components/ChatArea.tsx`
- Delete: `client/src/components/MessageBubble.tsx`
- Delete: `client/src/components/QueryResult.tsx`

- [ ] **Step 1: 创建目录结构**

```bash
cd client
mkdir -p src/{app,features/chat/{components,hooks},features/query-result/components,features/sidebar/components,types}
```

- [ ] **Step 2: 创建全局类型**

`client/src/types/index.ts`:

```ts
import type { QueryResponse, QueryRequest, ApiError } from "@/services/api"

export type { QueryResponse, QueryRequest, ApiError }
```

- [ ] **Step 3: 创建 Chat 类型**

`client/src/features/chat/types.ts`:

```ts
export interface ChatMessage {
  id: number
  role: "user" | "ai"
  content: string
}

export interface UseChatReturn {
  messages: ChatMessage[]
  isLoading: boolean
  error: string | null
  sendMessage: (sql: string) => Promise<void>
}
```

- [ ] **Step 4: 创建 QueryResult 类型**

`client/src/features/query-result/types.ts`:

```ts
export interface QueryResultData {
  columns: string[]
  rows: Record<string, unknown>[]
  rowCount: number
  durationMs: number
}
```

- [ ] **Step 5: 删除旧组件**

```bash
cd client
rm src/components/ChatArea.tsx src/components/MessageBubble.tsx src/components/QueryResult.tsx
```

- [ ] **Step 6: 提交**

```bash
cd client
git add types/ features/ -A
git rm src/components/ChatArea.tsx src/components/MessageBubble.tsx src/components/QueryResult.tsx
git commit -m "refactor: create feature-based directory structure and types, remove old components"
```

---

## Task 3: 实现 useChat Hook

**Files:**
- Create: `client/src/features/chat/hooks/use-chat.ts`

- [ ] **Step 1: 实现 useChat hook**

`client/src/features/chat/hooks/use-chat.ts`:

```ts
import { useState, useCallback } from "react"
import { executeQuery } from "@/services/api"
import type { ChatMessage, UseChatReturn } from "@/features/chat/types"

export function useChat(): UseChatReturn {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 0,
      role: "ai",
      content: "你好！我是数据库助手，请输入你的查询。",
    },
  ])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const sendMessage = useCallback(async (sql: string) => {
    if (!sql.trim() || isLoading) return

    const userMsg: ChatMessage = {
      id: Date.now(),
      role: "user",
      content: sql,
    }
    setMessages((prev) => [...prev, userMsg])
    setIsLoading(true)
    setError(null)

    try {
      const result = await executeQuery({
        connectionId: "demo",
        sql,
      })

      const aiMsg: ChatMessage = {
        id: Date.now() + 1,
        role: "ai",
        content: `查询完成，返回 ${result.rowCount} 行数据，耗时 ${result.durationMs}ms。`,
      }
      setMessages((prev) => [...prev, aiMsg])
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : "未知错误"
      setError(errorMessage)

      const errorMsg: ChatMessage = {
        id: Date.now() + 1,
        role: "ai",
        content: `查询失败: ${errorMessage}`,
      }
      setMessages((prev) => [...prev, errorMsg])
    } finally {
      setIsLoading(false)
    }
  }, [isLoading])

  return { messages, isLoading, error, sendMessage }
}
```

- [ ] **Step 2: 提交**

```bash
cd client
git add src/features/chat/hooks/use-chat.ts
git commit -m "feat: implement useChat hook with message management and API integration"
```

---

## Task 4: 实现 Chat 功能组件

**Files:**
- Create: `client/src/features/chat/components/chat-input.tsx`
- Create: `client/src/features/chat/components/message-item.tsx`
- Create: `client/src/features/chat/components/chat-area.tsx`

- [ ] **Step 1: 实现 ChatInput 组件**

`client/src/features/chat/components/chat-input.tsx`:

```tsx
import { useState } from "react"
import { InputGroup } from "@/components/input-group"
import { AutosizeTextarea } from "@/components/autosize-textarea"
import { Button } from "@/components/ui/button"
import { ArrowUpIcon } from "lucide-react"

interface ChatInputProps {
  onSubmit: (value: string) => void
  disabled?: boolean
}

export function ChatInput({ onSubmit, disabled = false }: ChatInputProps) {
  const [prompt, setPrompt] = useState("")

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    if (!prompt.trim()) return
    onSubmit(prompt)
    setPrompt("")
  }

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      handleSubmit(event)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="w-full">
      <InputGroup>
        <AutosizeTextarea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入查询，例如：查询 users 表的所有数据"
          minRows={1}
          maxRows={5}
          className="resize-none"
          disabled={disabled}
        />
        <div className="flex items-center justify-end p-2">
          <Button
            type="submit"
            size="icon"
            variant="ghost"
            className="rounded-full"
            disabled={!prompt.trim() || disabled}
          >
            <ArrowUpIcon className="h-4 w-4" />
          </Button>
        </div>
      </InputGroup>
    </form>
  )
}
```

- [ ] **Step 2: 实现 MessageItem 组件**

`client/src/features/chat/components/message-item.tsx`:

```tsx
import { cn } from "@/lib/utils"
import { Card } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

interface MessageItemProps {
  role: "user" | "ai"
  content: string
  className?: string
  loading?: boolean
}

export function MessageItem({
  role,
  content,
  className,
  loading = false,
}: MessageItemProps) {
  if (loading) {
    return (
      <div className={cn("flex w-full justify-start", className)}>
        <Card className="max-w-[80%] rounded-lg px-4 py-3">
          <Skeleton className="h-4 w-48" />
        </Card>
      </div>
    )
  }

  return (
    <div
      className={cn("flex w-full", role === "user" ? "justify-end" : "justify-start", className)}
    >
      <Card
        className={cn(
          "max-w-[80%] rounded-lg px-4 py-3 text-sm",
          role === "user"
            ? "bg-primary text-primary-foreground"
            : "bg-muted text-muted-foreground"
        )}
      >
        {content}
      </Card>
    </div>
  )
}
```

- [ ] **Step 3: 实现 ChatArea 组件**

`client/src/features/chat/components/chat-area.tsx`:

```tsx
import { ScrollArea } from "@/components/ui/scroll-area"
import { MessageItem } from "@/features/chat/components/message-item"
import { ChatInput } from "@/features/chat/components/chat-input"
import type { ChatMessage } from "@/features/chat/types"

interface ChatAreaProps {
  messages: ChatMessage[]
  isLoading: boolean
  onSend: (value: string) => void
}

export function ChatArea({ messages, isLoading, onSend }: ChatAreaProps) {
  return (
    <div className="flex h-full flex-col">
      <ScrollArea className="flex-1 p-4">
        <div className="space-y-4">
          {messages.map((msg) => (
            <MessageItem
              key={msg.id}
              role={msg.role}
              content={msg.content}
            />
          ))}
          {isLoading && (
            <MessageItem role="ai" content="Thinking..." loading />
          )}
        </div>
      </ScrollArea>
      <div className="border-t p-4">
        <ChatInput onSubmit={onSend} disabled={isLoading} />
      </div>
    </div>
  )
}
```

- [ ] **Step 4: 提交**

```bash
cd client
git add src/features/chat/components/chat-input.tsx src/features/chat/components/message-item.tsx src/features/chat/components/chat-area.tsx
git commit -m "feat: implement chat feature components (input, message, area)"
```

---

## Task 5: 实现 QueryResult 功能组件

**Files:**
- Create: `client/src/features/query-result/components/result-table.tsx`
- Create: `client/src/features/query-result/components/query-result.tsx`

- [ ] **Step 1: 实现 ResultTable 组件**

`client/src/features/query-result/components/result-table.tsx`:

```tsx
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ScrollArea } from "@/components/ui/scroll-area"

interface ResultTableProps {
  columns: string[]
  rows: Record<string, unknown>[]
}

export function ResultTable({ columns, rows }: ResultTableProps) {
  return (
    <ScrollArea className="h-full">
      <Table>
        <TableHeader className="sticky top-0 bg-background">
          <TableRow>
            {columns.map((col) => (
              <TableHead key={col}>{col}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row, i) => (
            <TableRow key={i}>
              {columns.map((col) => (
                <TableCell key={col}>
                  {row[col] !== null && row[col] !== undefined
                    ? String(row[col])
                    : "null"}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </ScrollArea>
  )
}
```

- [ ] **Step 2: 实现 QueryResult 组件**

`client/src/features/query-result/components/query-result.tsx`:

```tsx
import { Badge } from "@/components/ui/badge"
import { ResultTable } from "@/features/query-result/components/result-table"
import type { QueryResultData } from "@/features/query-result/types"

interface QueryResultProps {
  data: QueryResultData | null
}

export function QueryResult({ data }: QueryResultProps) {
  if (!data || data.rows.length === 0) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
        执行查询后结果将显示在这里
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
```

- [ ] **Step 3: 提交**

```bash
cd client
git add src/features/query-result/components/result-table.tsx src/features/query-result/components/query-result.tsx
git commit -m "feat: implement query result feature components (table, result)"
```

---

## Task 6: 重构 Sidebar 组件

**Files:**
- Create: `client/src/features/sidebar/components/app-sidebar.tsx`
- Create: `client/src/features/sidebar/components/nav-main.tsx`
- Create: `client/src/features/sidebar/components/nav-projects.tsx`
- Create: `client/src/features/sidebar/components/nav-user.tsx`
- Create: `client/src/features/sidebar/components/team-switcher.tsx`

- [ ] **Step 1: 实现 NavMain 组件**

`client/src/features/sidebar/components/nav-main.tsx`:

```tsx
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from "@/components/ui/sidebar"
import { ChevronRightIcon } from "lucide-react"

export interface NavItem {
  title: string
  url: string
  icon?: React.ReactNode
  isActive?: boolean
  items?: { title: string; url: string }[]
}

interface NavMainProps {
  items: NavItem[]
}

export function NavMain({ items }: NavMainProps) {
  return (
    <SidebarGroup>
      <SidebarGroupLabel>Platform</SidebarGroupLabel>
      <SidebarMenu>
        {items.map((item) => (
          <Collapsible
            key={item.title}
            defaultOpen={item.isActive}
            className="group/collapsible"
            asChild
          >
            <SidebarMenuItem>
              <CollapsibleTrigger asChild>
                <SidebarMenuButton tooltip={item.title}>
                  {item.icon}
                  <span>{item.title}</span>
                  <ChevronRightIcon className="ml-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90" />
                </SidebarMenuButton>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <SidebarMenuSub>
                  {item.items?.map((subItem) => (
                    <SidebarMenuSubItem key={subItem.title}>
                      <SidebarMenuSubButton asChild>
                        <a href={subItem.url}>
                          <span>{subItem.title}</span>
                        </a>
                      </SidebarMenuSubButton>
                    </SidebarMenuSubItem>
                  ))}
                </SidebarMenuSub>
              </CollapsibleContent>
            </SidebarMenuItem>
          </Collapsible>
        ))}
      </SidebarMenu>
    </SidebarGroup>
  )
}
```

- [ ] **Step 2: 实现 NavUser 组件**

`client/src/features/sidebar/components/nav-user.tsx`:

```tsx
import {
  Avatar,
  AvatarFallback,
  AvatarImage,
} from "@/components/ui/avatar"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar"
import { ChevronsUpDownIcon, BadgeCheckIcon, CreditCardIcon, BellIcon, LogOutIcon } from "lucide-react"

interface NavUserProps {
  user: {
    name: string
    email: string
    avatar: string
  }
}

export function NavUser({ user }: NavUserProps) {
  const { isMobile } = useSidebar()

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              className="data-[state=open]:bg-sidebar-accent"
            >
              <Avatar className="h-8 w-8 rounded-lg">
                <AvatarImage src={user.avatar} alt={user.name} />
                <AvatarFallback className="rounded-lg">
                  {user.name
                    .split(" ")
                    .map((n) => n[0])
                    .join("")
                    .toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-medium">{user.name}</span>
                <span className="truncate text-xs">{user.email}</span>
              </div>
              <ChevronsUpDownIcon className="ml-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="min-w-56 rounded-lg"
            side={isMobile ? "bottom" : "right"}
            align="end"
            sideOffset={4}
          >
            <DropdownMenuLabel className="p-0 font-normal">
              <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                <Avatar className="h-8 w-8 rounded-lg">
                  <AvatarImage src={user.avatar} alt={user.name} />
                  <AvatarFallback className="rounded-lg">
                    {user.name
                      .split(" ")
                      .map((n) => n[0])
                      .join("")
                      .toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-medium">{user.name}</span>
                  <span className="truncate text-xs">{user.email}</span>
                </div>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <DropdownMenuItem>
                <BadgeCheckIcon />
                Account
              </DropdownMenuItem>
              <DropdownMenuItem>
                <CreditCardIcon />
                Billing
              </DropdownMenuItem>
              <DropdownMenuItem>
                <BellIcon />
                Notifications
              </DropdownMenuItem>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem>
              <LogOutIcon />
              Log out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
```

- [ ] **Step 3: 实现 NavProjects 组件**

`client/src/features/sidebar/components/nav-projects.tsx`:

```tsx
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"
import { FolderIcon, type LucideIcon } from "lucide-react"

interface NavProject {
  name: string
  url: string
  icon: LucideIcon
}

interface NavProjectsProps {
  projects: NavProject[]
}

export function NavProjects({ projects }: NavProjectsProps) {
  return (
    <SidebarGroup className="group-data-[collapsible=icon]:hidden">
      <SidebarGroupLabel>Projects</SidebarGroupLabel>
      <SidebarMenu>
        {projects.map((item) => (
          <SidebarMenuItem key={item.name}>
            <SidebarMenuButton asChild>
              <a href={item.url}>
                <item.icon />
                <span>{item.name}</span>
              </a>
            </SidebarMenuButton>
          </SidebarMenuItem>
        ))}
      </SidebarMenu>
    </SidebarGroup>
  )
}
```

- [ ] **Step 4: 实现 TeamSwitcher 组件**

`client/src/features/sidebar/components/team-switcher.tsx`:

```tsx
import * as React from "react"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar"
import { ChevronsUpDownIcon, PlusIcon, type LucideIcon } from "lucide-react"

interface Team {
  name: string
  logo: LucideIcon
  plan: string
}

interface TeamSwitcherProps {
  teams: Team[]
}

export function TeamSwitcher({ teams }: TeamSwitcherProps) {
  const { isMobile } = useSidebar()
  const [activeTeam, setActiveTeam] = React.useState(teams[0])

  if (!activeTeam) {
    return null
  }

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              className="data-[state=open]:bg-sidebar-accent"
            >
              <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
                <activeTeam.logo className="size-4" />
              </div>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-medium">{activeTeam.name}</span>
                <span className="truncate text-xs">{activeTeam.plan}</span>
              </div>
              <ChevronsUpDownIcon className="ml-auto" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="min-w-56 rounded-lg"
            align="start"
            side={isMobile ? "bottom" : "right"}
            sideOffset={4}
          >
            <DropdownMenuLabel className="text-xs text-muted-foreground">
              Teams
            </DropdownMenuLabel>
            {teams.map((team, index) => (
              <DropdownMenuItem
                key={team.name}
                onClick={() => setActiveTeam(team)}
                className="gap-2 p-2"
              >
                <div className="flex size-6 items-center justify-center rounded-md border">
                  <team.logo className="size-4" />
                </div>
                {team.name}
              </DropdownMenuItem>
            ))}
            <DropdownMenuSeparator />
            <DropdownMenuItem className="gap-2 p-2">
              <div className="flex size-6 items-center justify-center rounded-md border">
                <PlusIcon className="size-4" />
              </div>
              <div className="font-medium text-muted-foreground">
                Add team
              </div>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
```

- [ ] **Step 5: 实现 AppSidebar 组件**

`client/src/features/sidebar/components/app-sidebar.tsx`:

```tsx
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarRail,
} from "@/components/ui/sidebar"
import { NavMain, type NavItem } from "@/features/sidebar/components/nav-main"
import { NavProjects } from "@/features/sidebar/components/nav-projects"
import { NavUser } from "@/features/sidebar/components/nav-user"
import { TeamSwitcher } from "@/features/sidebar/components/team-switcher"
import {
  GalleryVerticalEndIcon,
  TerminalSquareIcon,
  BotIcon,
  BookOpenIcon,
  Settings2Icon,
  type LucideIcon,
} from "lucide-react"

// TODO: Replace with real project data
const sidebarData = {
  user: {
    name: "Admin",
    email: "admin@example.com",
    avatar: "/avatars/admin.jpg",
  },
  teams: [
    { name: "Data Talk", logo: GalleryVerticalEndIcon, plan: "Demo" },
  ] as { name: string; logo: LucideIcon; plan: string }[],
  navMain: [
    {
      title: "Playground",
      url: "#",
      icon: <TerminalSquareIcon className="size-4" />,
      isActive: true,
      items: [
        { title: "History", url: "#" },
        { title: "Starred", url: "#" },
        { title: "Settings", url: "#" },
      ],
    },
    {
      title: "Models",
      url: "#",
      icon: <BotIcon className="size-4" />,
      items: [
        { title: "Genesis", url: "#" },
        { title: "Explorer", url: "#" },
        { title: "Quantum", url: "#" },
      ],
    },
    {
      title: "Documentation",
      url: "#",
      icon: <BookOpenIcon className="size-4" />,
      items: [
        { title: "Introduction", url: "#" },
        { title: "Get Started", url: "#" },
        { title: "Tutorials", url: "#" },
        { title: "Changelog", url: "#" },
      ],
    },
    {
      title: "Settings",
      url: "#",
      icon: <Settings2Icon className="size-4" />,
      items: [
        { title: "General", url: "#" },
        { title: "Team", url: "#" },
      ],
    },
  ] as NavItem[],
  projects: [
    { name: "Database Query", url: "#", icon: TerminalSquareIcon },
  ] as { name: string; url: string; icon: LucideIcon }[],
}

interface AppSidebarProps extends React.ComponentProps<typeof Sidebar> {}

export function AppSidebar({ ...props }: AppSidebarProps) {
  return (
    <Sidebar collapsible="icon" {...props}>
      <SidebarHeader>
        <TeamSwitcher teams={sidebarData.teams} />
      </SidebarHeader>
      <SidebarContent>
        <NavMain items={sidebarData.navMain} />
        <NavProjects projects={sidebarData.projects} />
      </SidebarContent>
      <SidebarFooter>
        <NavUser user={sidebarData.user} />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
```

- [ ] **Step 6: 删除旧 sidebar 组件**

```bash
cd client
rm src/components/app-sidebar.tsx src/components/nav-main.tsx src/components/nav-projects.tsx src/components/nav-user.tsx src/components/team-switcher.tsx
```

- [ ] **Step 7: 提交**

```bash
cd client
git add src/features/sidebar/components/ -A
git rm src/components/app-sidebar.tsx src/components/nav-main.tsx src/components/nav-projects.tsx src/components/nav-user.tsx src/components/team-switcher.tsx
git commit -m "refactor: rebuild sidebar components with clean shadcn patterns and real data interfaces"
```

---

## Task 7: 实现 App Layout 和入口文件

**Files:**
- Create: `client/src/app/layout.tsx`
- Modify: `client/src/App.tsx`
- Modify: `client/src/main.tsx`

- [ ] **Step 1: 实现 Layout 组件**

`client/src/app/layout.tsx`:

```tsx
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "@/features/sidebar/components/app-sidebar"
import { Separator } from "@/components/ui/separator"

interface LayoutProps {
  children: React.ReactNode
}

export function Layout({ children }: LayoutProps) {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <header className="flex h-12 shrink-0 items-center gap-2 border-b px-4">
          <h1 className="text-lg font-semibold">数据库查询助手</h1>
        </header>
        <Separator />
        <main className="flex-1">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  )
}
```

- [ ] **Step 2: 重写 App.tsx**

`client/src/App.tsx`:

```tsx
import { useState } from "react"
import { Layout } from "@/app/layout"
import { useChat } from "@/features/chat/hooks/use-chat"
import { ChatArea } from "@/features/chat/components/chat-area"
import { QueryResult } from "@/features/query-result/components/query-result"
import type { QueryResultData } from "@/features/query-result/types"
import type { QueryResponse } from "@/services/api"

function App() {
  const { messages, isLoading, sendMessage } = useChat()
  const [queryResult, setQueryResult] = useState<QueryResultData | null>(null)

  const handleSend = async (sql: string) => {
    // We need to intercept the query result to pass to QueryResult panel
    // For now, useChat manages its own state, and we track result separately
    await sendMessage(sql)
  }

  return (
    <Layout>
      <div className="flex h-[calc(100vh-3rem)]">
        <div className="w-1/2 border-r">
          <ChatArea
            messages={messages}
            isLoading={isLoading}
            onSend={handleSend}
          />
        </div>
        <div className="w-1/2">
          <QueryResult data={queryResult} />
        </div>
      </div>
    </Layout>
  )
}

export default App
```

> **注意**: 当前 App.tsx 中 queryResult 和 ChatArea 是分离的。useChat 内部调用 API 但不返回 queryResult。下一步 Step 3 会修复这个数据桥接问题。

- [ ] **Step 3: 修复 useChat hook 以返回查询结果**

更新 `client/src/features/chat/hooks/use-chat.ts`，增加 `queryResult` 状态：

```ts
import { useState, useCallback } from "react"
import { executeQuery } from "@/services/api"
import type { QueryResponse } from "@/services/api"
import type { ChatMessage, UseChatReturn } from "@/features/chat/types"

export interface UseChatReturn {
  messages: ChatMessage[]
  isLoading: boolean
  error: string | null
  queryResult: QueryResponse | null
  sendMessage: (sql: string) => Promise<void>
}

export function useChat(): UseChatReturn {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 0,
      role: "ai",
      content: "你好！我是数据库助手，请输入你的查询。",
    },
  ])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [queryResult, setQueryResult] = useState<QueryResponse | null>(null)

  const sendMessage = useCallback(async (sql: string) => {
    if (!sql.trim() || isLoading) return

    const userMsg: ChatMessage = {
      id: Date.now(),
      role: "user",
      content: sql,
    }
    setMessages((prev) => [...prev, userMsg])
    setIsLoading(true)
    setError(null)

    try {
      const result = await executeQuery({
        connectionId: "demo",
        sql,
      })

      setQueryResult(result)

      const aiMsg: ChatMessage = {
        id: Date.now() + 1,
        role: "ai",
        content: `查询完成，返回 ${result.rowCount} 行数据，耗时 ${result.durationMs}ms。`,
      }
      setMessages((prev) => [...prev, aiMsg])
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : "未知错误"
      setError(errorMessage)
      setQueryResult(null)

      const errorMsg: ChatMessage = {
        id: Date.now() + 1,
        role: "ai",
        content: `查询失败: ${errorMessage}`,
      }
      setMessages((prev) => [...prev, errorMsg])
    } finally {
      setIsLoading(false)
    }
  }, [isLoading])

  return { messages, isLoading, error, queryResult, sendMessage }
}
```

- [ ] **Step 4: 更新 App.tsx 桥接查询结果**

更新 `client/src/App.tsx`：

```tsx
import { Layout } from "@/app/layout"
import { useChat } from "@/features/chat/hooks/use-chat"
import { ChatArea } from "@/features/chat/components/chat-area"
import { QueryResult } from "@/features/query-result/components/query-result"
import type { QueryResultData } from "@/features/query-result/types"

function App() {
  const { messages, isLoading, queryResult, sendMessage } = useChat()

  const resultData: QueryResultData | null = queryResult
    ? {
        columns: queryResult.columns,
        rows: queryResult.rows,
        rowCount: queryResult.rowCount,
        durationMs: queryResult.durationMs,
      }
    : null

  return (
    <Layout>
      <div className="flex h-[calc(100vh-3rem)]">
        <div className="w-1/2 border-r">
          <ChatArea
            messages={messages}
            isLoading={isLoading}
            onSend={sendMessage}
          />
        </div>
        <div className="w-1/2">
          <QueryResult data={resultData} />
        </div>
      </div>
    </Layout>
  )
}

export default App
```

- [ ] **Step 5: 更新 main.tsx**

`client/src/main.tsx`:

```tsx
import React from "react"
import ReactDOM from "react-dom/client"
import App from "./App"
import "./index.css"
import { TooltipProvider } from "@/components/ui/tooltip"

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <TooltipProvider>
      <App />
    </TooltipProvider>
  </React.StrictMode>,
)
```

- [ ] **Step 6: 删除旧的 ui 组件中不再需要的文件**

检查是否有旧组件残留：

```bash
cd client
# 确保以下文件已被 shadcn 安装覆盖
ls src/components/ui/
```

- [ ] **Step 7: 验证 TypeScript 编译**

```bash
cd client
npx tsc --noEmit
```

预期：无错误。如果有类型错误，修复后重新验证。

- [ ] **Step 8: 提交**

```bash
cd client
git add src/app/layout.tsx src/App.tsx src/main.tsx src/features/chat/hooks/use-chat.ts
git commit -m "feat: wire up app layout, connect chat and query result through useChat hook"
```

---

## Task 8: 最终验证 + 清理

- [ ] **Step 1: 检查所有文件一致性**

```bash
cd client
# 确认旧组件已全部删除
find src -name "*.tsx" -o -name "*.ts" | sort
```

应包含的文件：
```
src/App.tsx
src/main.tsx
src/vite-env.d.ts
src/app/layout.tsx
src/components/ui/button.tsx
src/components/ui/card.tsx
src/components/ui/badge.tsx
src/components/ui/table.tsx
src/components/ui/tabs.tsx
src/components/ui/scroll-area.tsx
src/components/ui/alert.tsx
src/components/ui/skeleton.tsx
src/components/ui/separator.tsx
src/components/ui/tooltip.tsx
src/components/ui/avatar.tsx
src/components/ui/dropdown-menu.tsx
src/components/ui/collapsible.tsx
src/components/ui/sheet.tsx
src/components/ui/sidebar.tsx
src/components/ui/breadcrumb.tsx
src/components/ui/input.tsx
src/components/ui/textarea.tsx
src/components/input-group.tsx
src/components/autosize-textarea.tsx
src/features/chat/types.ts
src/features/chat/hooks/use-chat.ts
src/features/chat/components/chat-input.tsx
src/features/chat/components/message-item.tsx
src/features/chat/components/chat-area.tsx
src/features/query-result/types.ts
src/features/query-result/components/result-table.tsx
src/features/query-result/components/query-result.tsx
src/features/sidebar/components/app-sidebar.tsx
src/features/sidebar/components/nav-main.tsx
src/features/sidebar/components/nav-projects.tsx
src/features/sidebar/components/nav-user.tsx
src/features/sidebar/components/team-switcher.tsx
src/hooks/use-mobile.ts
src/lib/utils.ts
src/services/api.ts
src/types/index.ts
```

- [ ] **Step 2: TypeScript 编译检查**

```bash
cd client
npx tsc --noEmit
```

修复所有类型错误。

- [ ] **Step 3: Vite 构建检查**

```bash
cd client
npx vite build
```

确认无构建错误。

- [ ] **Step 4: 提交最终状态**

```bash
cd client
git add -A
git commit -m "refactor: complete frontend refactor to feature-based architecture with shadcn/ui"
```

---

## 风险与注意事项

1. **社区组件安装失败**: `InputGroup` 和 `AutosizeTextarea` 来自 originui.com registry，URL 可能变化。Task 1 已包含手动创建作为降级方案。
2. **shadcn 组件版本**: 当前项目使用 shadcn 4.2.0 + `base-nova` 风格，新安装组件应与此风格一致。
3. **旧 sidebar 的 `render` prop 语法**: 旧代码使用了 `render` prop（base-ui mergeProps 模式），新组件统一使用 `asChild` 替代，减少依赖。
4. **TypeScript strict 模式**: 项目启用 `noUnusedLocals` 和 `noUnusedParameters`，确保无未使用变量。
