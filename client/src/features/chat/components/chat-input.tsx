import { useState } from "react"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupTextarea,
} from "@/components/ui/input-group"
import { ArrowUpIcon } from "lucide-react"

interface ChatInputProps {
  onSubmit: (value: string) => void
  disabled?: boolean
  autoFocus?: boolean
}

export function ChatInput({ onSubmit, disabled = false, autoFocus = false }: ChatInputProps) {
  const [prompt, setPrompt] = useState("")

  const submit = () => {
    if (!prompt.trim()) return
    onSubmit(prompt)
    setPrompt("")
  }

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    submit()
  }

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <form onSubmit={handleSubmit} className="w-full">
      <InputGroup className="rounded-2xl bg-muted/50 shadow-sm ring-1 ring-border focus-within:ring-2 focus-within:ring-primary/20 focus-within:border-primary transition-all duration-200">
        <InputGroupTextarea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入查询，例如：查询 users 表的所有数据"
          disabled={disabled}
          autoFocus={autoFocus}
          className="min-h-[100px] px-4 py-3 text-base"
        />
        <InputGroupAddon align="block-end" className="px-3 pb-3">
          <div className="flex w-full items-center justify-between">
            <div className="text-xs text-muted-foreground">
              Shift + Enter 换行
            </div>
            <InputGroupButton
              type="submit"
              variant="default"
              size="icon-sm"
              className="rounded-xl bg-primary shadow-md hover:bg-primary/90 transition-all hover:scale-105 active:scale-95"
              disabled={!prompt.trim() || disabled}
              aria-label="发送"
            >
              <ArrowUpIcon className="size-5" />
            </InputGroupButton>
          </div>
        </InputGroupAddon>
      </InputGroup>
    </form>
  )
}
