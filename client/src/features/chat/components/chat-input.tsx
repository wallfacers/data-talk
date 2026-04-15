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
