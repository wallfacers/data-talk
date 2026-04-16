export function HeroView() {
  return (
    <div className="flex h-full items-center justify-center p-8">
      <div className="w-full max-w-2xl">
        <h1 className="mb-6 text-center text-xl font-light text-muted-foreground">
          问点什么，比如 "查询用户表最近一周的注册趋势"
        </h1>
        <div id="composer-slot" />
      </div>
    </div>
  )
}
