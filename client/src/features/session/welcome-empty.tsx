import type { ComponentType, SVGProps } from 'react'
import {
  DatabaseIcon,
  LineChartIcon,
  SparklesIcon,
  TableIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { useI18n } from '@/i18n/use-i18n'

type Sample = {
  icon: ComponentType<SVGProps<SVGSVGElement>>
  title: string
  hint: string
}

export function WelcomeEmpty() {
  const { t } = useI18n()
  const samples: Sample[] = [
    {
      icon: TableIcon,
      title: t('welcome.sample.users.title'),
      hint: t('welcome.sample.users.hint'),
    },
    {
      icon: LineChartIcon,
      title: t('welcome.sample.orders.title'),
      hint: t('welcome.sample.orders.hint'),
    },
    {
      icon: SparklesIcon,
      title: t('welcome.sample.products.title'),
      hint: t('welcome.sample.products.hint'),
    },
  ]
  return (
    <div className="flex h-full items-center justify-center px-6 py-12">
      <div className="w-full max-w-3xl">
        <div className="mb-10 flex flex-col items-center gap-3 text-center">
          <div className="flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
            <DatabaseIcon className="size-6" />
          </div>
          <h1 className="text-3xl font-semibold tracking-tight">DataTalk</h1>
          <p className="max-w-md text-sm text-muted-foreground">
            {t('welcome.subtitle')}
          </p>
        </div>

        <div className="mb-8 grid gap-3 md:grid-cols-3">
          {samples.map((s) => (
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
          <Button>{t('welcome.newConnection')}</Button>
          <Button variant="outline">{t('welcome.viewDocs')}</Button>
        </div>
      </div>
    </div>
  )
}
