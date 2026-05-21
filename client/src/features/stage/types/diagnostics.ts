export type ScanType = 'FULL_SCAN' | 'INDEX_RANGE' | 'INDEX_SCAN' | 'CONST' | 'REF' | 'OTHER'
export type Impact = 'HIGH' | 'MEDIUM' | 'LOW'

export interface ExplainNode {
  operation: string
  table?: string
  scanType: ScanType
  rows: number
  cost?: number
  extra?: string
  children: ExplainNode[]
}

export interface ExplainPlan {
  dialect: string
  rawText: string
  nodes: ExplainNode[]
  totalCostEstimate?: number
  warnings: string[]
}

export interface UnsupportedResult {
  unsupported: true
  reason: string
}

export interface DiagnosticErrorResult {
  error: {
    type: string
    message: string
  }
}

export interface IndexRecommendation {
  table: string
  columns: string[]
  indexType: string
  impact: Impact
  rationale: string
}

export interface IndexHintsResult {
  recommendations: IndexRecommendation[]
  summary: string
}

export type ExplainResult = ExplainPlan | UnsupportedResult | DiagnosticErrorResult
export type IndexHintsResponse = IndexHintsResult | UnsupportedResult | DiagnosticErrorResult
