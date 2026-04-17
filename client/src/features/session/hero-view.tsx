import { DatabaseIcon } from 'lucide-react'

export function HeroView() {
  return (
    <div className="flex h-full flex-col items-center justify-center px-6">
      <div className="flex flex-col items-center gap-3 text-center">
        <div className="flex size-10 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
          <DatabaseIcon className="size-5" />
        </div>
        <h1 className="text-xl font-semibold tracking-tight">DataTalk</h1>
        <p className="text-sm text-muted-foreground">
          用自然语言和你的数据库对话
        </p>
      </div>
      <div className="mt-8 w-full max-w-3xl">
        <div id="composer-slot" />
      </div>
    </div>
  )
}
