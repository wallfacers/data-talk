interface JsonTreeProps {
  data: unknown
  depth?: number
}

export function BeforeStateJsonTree({ data, depth = 0 }: JsonTreeProps) {
  if (data === null || data === undefined) {
    return <span className="font-mono text-[13px] text-text-muted">null</span>
  }
  if (typeof data !== 'object') {
    return <span className="font-mono text-[13px] text-text-base">{JSON.stringify(data)}</span>
  }
  if (Array.isArray(data)) {
    return (
      <div className="font-mono text-[13px]">
        <span className="text-text-muted">[</span>
        {data.map((item, i) => (
          <div key={i} className="pl-4">
            <BeforeStateJsonTree data={item} depth={depth + 1} />
            {i < data.length - 1 && <span className="text-text-muted">,</span>}
          </div>
        ))}
        <span className="text-text-muted">]</span>
      </div>
    )
  }
  const entries = Object.entries(data as Record<string, unknown>)
  return (
    <div className="font-mono text-[13px]">
      <span className="text-text-muted">{'{'}</span>
      {entries.map(([key, value], i) => (
        <div key={key} className="pl-4">
          <span className="text-accent-primary">{JSON.stringify(key)}</span>
          <span className="text-text-muted">: </span>
          <BeforeStateJsonTree data={value} depth={depth + 1} />
          {i < entries.length - 1 && <span className="text-text-muted">,</span>}
        </div>
      ))}
      <span className="text-text-muted">{'}'}</span>
    </div>
  )
}
