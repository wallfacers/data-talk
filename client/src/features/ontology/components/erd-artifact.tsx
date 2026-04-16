import type { Artifact } from '@/services/channel/event-reducer'

export function ErdArtifact({ artifact }: { artifact: Artifact }) {
  const payload = artifact.payload as any
  const nodes = payload?.nodes ?? []
  return (
    <div className="flex h-full flex-wrap gap-2 p-4">
      {nodes.map((node: any, i: number) => (
        <div key={i} className="rounded border bg-background p-3 text-xs">
          <div className="font-semibold">{node.name}</div>
          {(node.fields ?? []).map((f: any, fi: number) => (
            <div key={fi} className="text-muted-foreground">{f.name}: {f.type}</div>
          ))}
        </div>
      ))}
      {nodes.length === 0 && (
        <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
          ER 图暂无节点数据
        </div>
      )}
    </div>
  )
}
