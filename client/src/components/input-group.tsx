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
