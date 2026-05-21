import { classifySqlRisk } from '@/features/chat/components/helpers/risk'

export function shouldAutoRunDirectSql(sql: string): boolean {
  return classifySqlRisk(sql) === 'L1'
}
