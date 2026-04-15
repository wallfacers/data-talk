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
