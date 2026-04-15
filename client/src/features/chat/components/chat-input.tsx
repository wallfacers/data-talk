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
      <InputGroup className="rounded-2xl bg-background shadow-lg shadow-black/5 ring-1 ring-border focus-within:ring-2 focus-within:ring-primary/10 focus-within:border-primary transition-all duration-300 ease-in-out">
        <InputGroupTextarea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入您的数据库查询指令..."
          disabled={disabled}
          autoFocus={autoFocus}
          className="min-h-[120px] px-5 py-4 text-base placeholder:text-muted-foreground/50"
        />
        <InputGroupAddon align="block-end" className="px-4 pb-4">
          <div className="flex w-full items-center justify-between">
            <div className="text-[11px] font-medium text-muted-foreground/60 flex items-center gap-1.5">
              <span className="rounded border px-1 py-0.5 leading-none bg-muted/50">Enter</span>
              发送指令
            </div>
            <InputGroupButton
              type="submit"
              variant="default"
              size="icon-sm"
              className="rounded-xl bg-primary shadow-sm hover:bg-primary/90 transition-all hover:-translate-y-0.5 active:translate-y-0"
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
