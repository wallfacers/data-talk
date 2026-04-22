import { describe, expect, it } from 'vitest'
import { shouldAutoRunDirectSql } from '../direct-sql-auto-run-policy'

describe('shouldAutoRunDirectSql', () => {
  it('returns true for low-risk SELECT statements', () => {
    expect(shouldAutoRunDirectSql('select * from orders')).toBe(true)
    expect(shouldAutoRunDirectSql(' -- comment\nEXPLAIN SELECT 1')).toBe(true)
  })

  it('returns false for unknown/higher-risk statements', () => {
    expect(shouldAutoRunDirectSql('with cte as (select 1) select * from cte')).toBe(false)
    expect(shouldAutoRunDirectSql('update orders set status = 1')).toBe(false)
  })
})
