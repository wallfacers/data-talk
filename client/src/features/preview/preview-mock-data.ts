// 纯前端特效预览用的假数据。数值故意编得有节奏感，便于看折线形状。

export const MOCK_SESSION_ID = 'preview-session'
export const MOCK_CONNECTION_ID = 'preview-conn'

export const MOCK_USER_PROMPT = '查询用户表近一周的注册趋势，画成折线图'
export const MOCK_GREEN_PROMPT = '换成绿色'

export const MOCK_TABLE_COLUMNS = ['day', 'registrations']

export const MOCK_TABLE_ROWS = [
  { day: '2026-04-10', registrations: 42 },
  { day: '2026-04-11', registrations: 58 },
  { day: '2026-04-12', registrations: 71 },
  { day: '2026-04-13', registrations: 63 },
  { day: '2026-04-14', registrations: 89 },
  { day: '2026-04-15', registrations: 95 },
  { day: '2026-04-16', registrations: 107 },
]

const DAYS = MOCK_TABLE_ROWS.map((r) => r.day)
const VALUES = MOCK_TABLE_ROWS.map((r) => r.registrations)

export function makeEchartsOption(color: string) {
  return {
    color: [color],
    xAxis: { type: 'category', data: DAYS },
    yAxis: { type: 'value' },
    series: [{ type: 'line', name: '注册数', data: VALUES }],
  }
}

export const MOCK_ACTION_IDS = [
  'datatalk.read_schema',
  'datatalk.execute_sql',
  'datatalk.render_chart',
] as const

export const MOCK_ACTION_DESCRIPTIONS: Record<string, string> = {
  'datatalk.read_schema': '读取数据库表结构',
  'datatalk.execute_sql': '执行只读 SQL 并产出表工件',
  'datatalk.render_chart': '根据 ECharts 配置渲染图表',
}
