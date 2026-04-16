import type { ComponentType, SVGProps } from 'react'
import {
  DatabaseIcon,
  LineChartIcon,
  SparklesIcon,
  TableIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

type Sample = {
  icon: ComponentType<SVGProps<SVGSVGElement>>
  title: string
  hint: string
}

const SAMPLES: Sample[] = [
  {
    icon: TableIcon,
    title: '查询用户表',
    hint: '最近一周的注册趋势',
  },
  {
    icon: LineChartIcon,
    title: '统计订单金额',
    hint: '最近 30 天总额并按天分桶',
  },
  {
    icon: SparklesIcon,
    title: '热销商品榜',
    hint: '近 90 天销量 Top 10',
  },
]

export function WelcomeEmpty() {
  return (
    <div className="flex h-full items-center justify-center px-6 py-12">
      <div className="w-full max-w-3xl">
        <div className="mb-10 flex flex-col items-center gap-3 text-center">
          <div className="flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
            <DatabaseIcon className="size-6" />
          </div>
          <h1 className="text-3xl font-semibold tracking-tight">DataTalk</h1>
          <p className="max-w-md text-sm text-muted-foreground">
            用自然语言和你的数据库对话。先从左侧选择一个连接并新建会话，或试试下面的示例。
          </p>
        </div>

        <div className="mb-8 grid gap-3 md:grid-cols-3">
          {SAMPLES.map((s) => (
            <Card
              key={s.title}
              className="group cursor-pointer border-dashed transition-colors hover:border-primary hover:bg-accent/40"
            >
              <CardContent className="flex flex-col gap-2 p-4">
                <s.icon className="size-4 text-muted-foreground transition-colors group-hover:text-primary" />
                <div className="text-sm font-medium">{s.title}</div>
                <div className="text-xs text-muted-foreground">{s.hint}</div>
              </CardContent>
            </Card>
          ))}
        </div>

        <div className="flex justify-center gap-2">
          <Button>新建连接</Button>
          <Button variant="outline">查看文档</Button>
        </div>
      </div>
    </div>
  )
}
