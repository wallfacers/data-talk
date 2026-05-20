import { cn } from '@/lib/utils'

export function TabContentLoader({ className }: { className?: string }) {
  return (
    <div
      role="status"
      aria-label="Loading"
      className={cn(
        'flex h-full w-full flex-col items-center justify-center gap-3 bg-background',
        className,
      )}
    >
      <svg
        className="h-5 w-5 animate-spin text-text-soft"
        viewBox="0 0 24 24"
        fill="none"
      >
        <circle
          className="opacity-25"
          cx="12"
          cy="12"
          r="10"
          stroke="currentColor"
          strokeWidth="3"
        />
        <path
          className="opacity-75"
          fill="currentColor"
          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
        />
      </svg>
      <span className="text-sm text-text-soft">加载中</span>
    </div>
  )
}
