import { DatabaseIcon, NetworkIcon, PieChartIcon, LayoutDashboardIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

type ToolApp = {
  id: string
  name: string
  icon: React.ElementType
  color: string
}

const TOOLS: ToolApp[] = [
  { id: 'sql', name: 'SQL 查询器', icon: DatabaseIcon, color: 'text-blue-500' },
  { id: 'er', name: 'ER 图设计器', icon: NetworkIcon, color: 'text-emerald-500' },
  { id: 'report', name: '报表分析器', icon: PieChartIcon, color: 'text-purple-500' },
  { id: 'dashboard', name: '仪表盘', icon: LayoutDashboardIcon, color: 'text-orange-500' },
]

export function StageDock() {
  return (
    <div className="flex items-center gap-2 rounded-2xl bg-background/80 backdrop-blur-md border border-border/50 p-2 shadow-2xl transition-all hover:bg-background/95">
      {TOOLS.map((tool) => (
        <button
          key={tool.id}
          type="button"
          className="group relative flex size-12 flex-col items-center justify-center rounded-xl transition-all duration-200 hover:-translate-y-2 hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <tool.icon className={cn("size-6 transition-transform duration-200 group-hover:scale-110", tool.color)} />
          
          {/* Tooltip (Mac Dock 风格) */}
          <span className="absolute -top-12 left-1/2 -translate-x-1/2 scale-0 whitespace-nowrap rounded-lg border border-border/50 bg-popover px-3 py-1.5 text-xs font-medium text-popover-foreground shadow-lg transition-all duration-200 group-hover:scale-100">
            {tool.name}
            {/* 倒三角小箭头 */}
            <span className="absolute -bottom-1 left-1/2 -z-10 size-2 -translate-x-1/2 rotate-45 border-b border-r border-border/50 bg-popover" />
          </span>
        </button>
      ))}
    </div>
  )
}
