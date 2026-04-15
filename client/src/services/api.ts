const API_BASE = "http://localhost:8080/api";

export interface QueryRequest {
  connectionId: string;
  sql: string;
}

export interface QueryResponse {
  columns: string[];
  rows: Record<string, unknown>[];
  durationMs: number;
  rowCount: number;
}

export interface ApiError {
  error: string;
  code: string;
}

export async function executeQuery(
  request: QueryRequest,
): Promise<QueryResponse> {
  const response = await fetch(`${API_BASE}/query`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const error: ApiError = await response.json();
    throw new Error(error.error || "Query failed");
  }

  return response.json();
}

export async function healthCheck(): Promise<{ status: string }> {
  const response = await fetch(`${API_BASE}/health`);
  return response.json();
}
