interface BeforeStateDiffProps {
  beforeState: Record<string, unknown>[]
}

export function BeforeStateDiff({ beforeState }: BeforeStateDiffProps) {
  if (!beforeState || beforeState.length === 0) return null

  return (
    <div className="space-y-1">
      {beforeState.map((row, i) => (
        <div key={i} className="rounded-md bg-bg-subtle p-2 font-mono text-[13px]">
          {Object.entries(row).map(([key, value]) => (
            <div key={key} className="flex gap-2">
              <span className="text-text-muted">{key}:</span>
              <span className="text-text-base">{value == null ? 'NULL' : String(value)}</span>
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}
