import { describe, expect, it } from 'vitest'
import { parseSqlOutline } from './parse-sql-outline'

describe('parseSqlOutline', () => {
  it('splits statements while ignoring semicolons in comments and strings', () => {
    const outline = parseSqlOutline(`-- ignore ; here
select 'semi;colon' as label from dual;
update users set name = 'semi; colon'
where id = 1;
delete from sessions;`)

    expect(outline).toEqual([
      {
        line: 2,
        kind: 'SELECT',
        summary: "select 'semi;colon' as label from dual",
        highRiskHint: false,
      },
      {
        line: 3,
        kind: 'UPDATE',
        summary: "update users set name = 'semi; colon' where id = 1",
        highRiskHint: false,
      },
      {
        line: 5,
        kind: 'DELETE',
        summary: 'delete from sessions',
        highRiskHint: true,
      },
    ])
  })

  it('flags high-risk ddl and dml statements without where', () => {
    const outline = parseSqlOutline(`drop table audit_log;
truncate table temp;
alter table users add column x int;
update users set active = 0;
delete from sessions where id = 1;`)

    expect(outline.map((statement) => statement.kind)).toEqual([
      'DROP',
      'TRUNCATE',
      'ALTER',
      'UPDATE',
      'DELETE',
    ])
    expect(outline.map((statement) => statement.highRiskHint)).toEqual([
      true,
      true,
      true,
      true,
      false,
    ])
  })

  it('recognizes SQLite pragmas and file-level maintenance commands', () => {
    const outline = parseSqlOutline(`pragma table_info(users);
explain query plan select * from users;
attach database 'other.db' as other;
vacuum;`)

    expect(outline.map((statement) => statement.kind)).toEqual([
      'PRAGMA',
      'EXPLAIN',
      'ATTACH',
      'VACUUM',
    ])
    expect(outline.map((statement) => statement.highRiskHint)).toEqual([
      false,
      false,
      true,
      true,
    ])
  })
})
