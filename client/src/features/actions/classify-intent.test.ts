import { describe, it, expect } from 'vitest'
import { classifyIntent } from './classify-intent'

describe('classifyIntent', () => {
  it('classifies DB-related Chinese queries', () => {
    expect(classifyIntent('查询用户表')).toBe('db_related')
    expect(classifyIntent('查一下订单')).toBe('db_related')
    expect(classifyIntent('统计报表')).toBe('db_related')
  })

  it('classifies DB-related English queries', () => {
    expect(classifyIntent('SELECT * FROM users')).toBe('db_related')
    expect(classifyIntent('count of orders')).toBe('db_related')
  })

  it('classifies non-DB queries as other', () => {
    expect(classifyIntent('hello world')).toBe('other')
    expect(classifyIntent('天气怎么样')).toBe('other')
    expect(classifyIntent('画一幅画')).toBe('other')
  })
})
