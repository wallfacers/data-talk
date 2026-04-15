import { cn } from "@/lib/utils"
import { Skeleton } from "@/components/ui/skeleton"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { UserIcon, BotIcon } from "lucide-react"

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
  const isUser = role === "user"

  if (loading) {
    return (
      <div className={cn("flex w-full gap-3", className)}>
        <Avatar size="sm" className="mt-1">
          <AvatarFallback className="bg-primary/10 text-primary">
            <BotIcon className="size-4" />
          </AvatarFallback>
        </Avatar>
        <div className="flex-1 space-y-2">
          <Skeleton className="h-4 w-[250px]" />
          <Skeleton className="h-4 w-[200px]" />
        </div>
      </div>
    )
  }

  return (
    <div
      className={cn(
        "flex w-full gap-3 py-2",
        isUser ? "flex-row-reverse" : "flex-row",
        className
      )}
    >
      <Avatar size="sm" className={cn("mt-1 shrink-0", isUser ? "bg-primary" : "bg-muted")}>
        {isUser ? (
          <AvatarFallback className="text-primary-foreground">
            <UserIcon className="size-4" />
          </AvatarFallback>
        ) : (
          <AvatarFallback>
            <BotIcon className="size-4" />
          </AvatarFallback>
        )}
      </Avatar>
      <div
        className={cn(
          "flex flex-col gap-1 max-w-[85%]",
          isUser ? "items-end" : "items-start"
        )}
      >
        <div
          className={cn(
            "rounded-2xl px-4 py-2 text-sm leading-relaxed",
            isUser
              ? "bg-primary text-primary-foreground shadow-sm"
              : "bg-muted/50 text-foreground"
          )}
        >
          {content}
        </div>
      </div>
    </div>
  )
}
