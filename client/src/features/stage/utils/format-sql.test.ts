import { describe, expect, it } from 'vitest'
import { formatSql, resolveSqlFormatterLanguage } from './format-sql'

describe('formatSql', () => {
  it('formats SQL with the shared pretty-print config', () => {
    expect(
      formatSql('select id,name from users where a=1 and b=2 order by created_at desc;', 'postgres'),
    ).toBe(`SELECT
  id,
  name
FROM
  users
WHERE
  a = 1
  AND b = 2
ORDER BY
  created_at DESC;`)
  })

  it('returns whitespace-only SQL unchanged', () => {
    expect(formatSql('   \n\t ', 'mysql')).toBe('   \n\t ')
  })

  it('falls back to the generic sql dialect for unknown connection kinds', () => {
    expect(resolveSqlFormatterLanguage('oracle')).toBe('sql')
    expect(formatSql('select 1', 'oracle')).toBe(`SELECT
  1`)
  })
})
