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
