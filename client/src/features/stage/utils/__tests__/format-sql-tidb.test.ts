import { describe, expect, it } from 'vitest'
import { formatSql, resolveSqlFormatterLanguage } from '../format-sql'

describe('formatSql — TiDB', () => {
  it('maps TiDB to mysql dialect', () => {
    expect(resolveSqlFormatterLanguage('tidb')).toBe('mysql')
  })

  it('formats TiDB SQL identically to MySQL', () => {
    const sql = 'SELECT * FROM t1 WHERE id = 1;'
    expect(formatSql(sql, 'tidb')).toBe(formatSql(sql, 'mysql'))
  })

  it('formats a representative TiDB query using mysql dialect rules', () => {
    expect(
      formatSql('select id, name from users where status = 1 and role = "admin" order by id desc;', 'tidb'),
    ).toBe(`SELECT
  id,
  name
FROM
  users
WHERE
  status = 1
  AND role = "admin"
ORDER BY
  id DESC;`)
  })
})
