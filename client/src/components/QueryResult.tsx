import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

interface QueryResultProps {
  columns: string[];
  rows: Record<string, unknown>[];
  rowCount: number;
  durationMs: number;
}

export function QueryResult({ columns, rows, rowCount, durationMs }: QueryResultProps) {
  if (rows.length === 0) {
    return (
      <div className="flex h-full items-center justify-center text-muted-foreground">
        No results
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col p-4">
      <div className="mb-2 flex items-center gap-2 text-xs text-muted-foreground">
        <span>{rowCount} rows</span>
        <span>•</span>
        <span>{durationMs}ms</span>
      </div>
      <div className="flex-1 overflow-auto">
        <Table>
          <TableHeader>
            <TableRow>
              {columns.map((col) => (
                <TableHead key={col}>{col}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row, i) => (
              <TableRow key={i}>
                {columns.map((col) => (
                  <TableCell key={col}>
                    {row[col] !== null && row[col] !== undefined
                      ? String(row[col])
                      : "null"}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
