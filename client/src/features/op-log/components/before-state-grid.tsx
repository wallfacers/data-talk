interface BeforeStateGridProps {
  rows: Record<string, unknown>[]
}

export function BeforeStateGrid({ rows }: BeforeStateGridProps) {
  if (rows.length === 0) return null
  const columns = Object.keys(rows[0])
  return (
    <div className="overflow-x-auto rounded-md border border-border-default">
      <table className="w-full border-collapse text-[13px]">
        <thead>
          <tr className="bg-bg-subtle">
            {columns.map(col => (
              <th key={col} className="border-b border-border-default px-2 py-1 text-left font-medium text-text-muted">
                {col}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i} className="border-b border-border-subtle hover:bg-interaction-hover">
              {columns.map(col => (
                <td key={col} className="px-2 py-1 font-mono text-text-base">
                  {row[col] == null ? <span className="text-text-muted">NULL</span> : String(row[col])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
