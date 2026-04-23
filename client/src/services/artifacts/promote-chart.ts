const BASE = (() => {
  const env = (import.meta as any).env?.VITE_API_BASE_URL
  return typeof env === 'string' && env.length > 0 ? env.replace(/\/$/, '') : ''
})()

export type PromoteChartArgs = {
  sessionId: string
  option: Record<string, unknown>
  sourceArtifactId?: string | null
  originMessageId?: string | null
  originPartId?: string | null
}

export type PromoteChartResponse = {
  artifactId: string
  version: number
}

export async function promoteChartToStage(args: PromoteChartArgs): Promise<PromoteChartResponse> {
  const body: Record<string, unknown> = {
    echartsOption: args.option,
  }
  if (args.sourceArtifactId != null) body.sourceArtifactId = args.sourceArtifactId
  if (args.originMessageId != null) body.originMessageId = args.originMessageId
  if (args.originPartId != null) body.originPartId = args.originPartId

  const res = await fetch(`${BASE}/api/sessions/${args.sessionId}/artifacts/chart`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    const bodyText = await res.text()
    throw new Error(`POST /api/sessions/${args.sessionId}/artifacts/chart failed with ${res.status}: ${bodyText}`)
  }

  return res.json() as Promise<PromoteChartResponse>
}
