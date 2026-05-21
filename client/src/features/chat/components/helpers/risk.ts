import type { ToolPart } from '@/services/channel/types'
import type { ActionDescriptor } from '@/features/actions/registry'

export type RiskLevel = 'L1' | 'L2' | 'L3'

const RISK_STYLES: Record<RiskLevel, { dot: string; border: string; label: string }> = {
  L1: { dot: 'bg-green-500', border: 'border-green-500/40', label: 'L1' },
  L2: { dot: 'bg-yellow-500', border: 'border-yellow-500/40', label: 'L2' },
  L3: { dot: 'bg-red-500', border: 'border-red-500/40', label: 'L3' },
}

export function getRiskStyles(risk: RiskLevel | null) {
  return risk ? RISK_STYLES[risk] : null
}

export function stripComments(sql: string): string {
  return sql.replace(/\/\*[\s\S]*?\*\//g, '').replace(/--.*$/gm, '').trim()
}

export function classifySqlRisk(sql: string): RiskLevel | null {
  const clean = stripComments(sql).replace(/^\s+/, '')
  if (/^(SELECT|EXPLAIN|SHOW|DESC(?:RIBE)?)\b/i.test(clean)) return 'L1'
  if (/^(INSERT|UPDATE|CREATE\s+(?:INDEX|VIEW))\b/i.test(clean)) return 'L2'
  if (/^(DELETE|DROP|ALTER|TRUNCATE|GRANT|REVOKE)\b/i.test(clean)) return 'L3'
  return null
}

export function resolveRisk(
  part: ToolPart | undefined,
  descriptor: Pick<ActionDescriptor, 'riskLevel'> | undefined,
): RiskLevel | null {
  const pLevel = part?.state?.metadata?.riskLevel
  if (pLevel === 'L1' || pLevel === 'L2' || pLevel === 'L3') return pLevel
  const dLevel = descriptor?.riskLevel
  if (dLevel === 'L1' || dLevel === 'L2' || dLevel === 'L3') return dLevel
  const sql = (part as any)?.state?.input?.sql
  if (typeof sql === 'string') {
    const r = classifySqlRisk(sql)
    if (r) return r
  }
  return null
}
