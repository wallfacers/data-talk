const KEYWORDS = /(表|字段|查询|查一下|查下|SELECT|FROM|WHERE|JOIN|count|average|趋势|报表|统计|用户表|订单|数据库|schema|ER 图|ER图)/i
export function classifyIntent(text: string): 'db_related' | 'other' {
  return KEYWORDS.test(text) ? 'db_related' : 'other'
}
