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

  it('maps Oracle to plsql dialect', () => {
    expect(resolveSqlFormatterLanguage('oracle')).toBe('plsql')
    expect(formatSql('select 1 from dual', 'oracle')).toBe(`SELECT
  1
FROM
  dual`)
  })

  it('maps SQL Server to transactsql dialect', () => {
    expect(resolveSqlFormatterLanguage('sqlserver')).toBe('transactsql')
  })

  it('maps MariaDB to mysql dialect', () => {
    expect(resolveSqlFormatterLanguage('mariadb')).toBe('mysql')
  })

  it('falls back to the generic sql dialect for unknown connection kinds', () => {
    expect(resolveSqlFormatterLanguage('clickhouse')).toBe('sql')
  })

  it('uses the generic sql formatter for SQLite', () => {
    expect(resolveSqlFormatterLanguage('sqlite')).toBe('sql')
    expect(formatSql('select id from users where active=1', 'sqlite')).toBe(`SELECT
  id
FROM
  users
WHERE
  active = 1`)
  })
})
