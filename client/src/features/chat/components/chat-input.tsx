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
}

export function ChatInput({ onSubmit, disabled = false }: ChatInputProps) {
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
      <InputGroup>
        <InputGroupTextarea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入查询，例如：查询 users 表的所有数据"
          disabled={disabled}
        />
        <InputGroupAddon align="block-end">
          <InputGroupButton
            type="submit"
            variant="default"
            size="icon-sm"
            className="ml-auto rounded-full"
            disabled={!prompt.trim() || disabled}
            aria-label="发送"
          >
            <ArrowUpIcon />
          </InputGroupButton>
        </InputGroupAddon>
      </InputGroup>
    </form>
  )
}
