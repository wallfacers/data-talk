import { ChartColumnIcon, DatabaseIcon, LayoutDashboardIcon, RowsIcon } from 'lucide-react'
import type { ComponentType } from 'react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore } from '@/stores/stage-store'
import { openOrFocusStageToolTab } from '@/features/stage/utils/open-or-focus-stage-tool-tab'

type ToolConfig = {
  tool: 'sql' | 'er' | 'report' | 'dashboard'
  icon: ComponentType<{ className?: string }>
  enabled: boolean
}

const TOOLS: ToolConfig[] = [
  { tool: 'sql', icon: DatabaseIcon, enabled: true },
  { tool: 'er', icon: RowsIcon, enabled: false },
  { tool: 'report', icon: ChartColumnIcon, enabled: false },
  { tool: 'dashboard', icon: LayoutDashboardIcon, enabled: false },
]

export function StageToolRow({
  sessionId,
  collapsed = false,
}: {
  sessionId: string | null
  collapsed?: boolean
}) {
  const { t } = useI18n()

  function focusWorkspaceTabInStage() {
    if (!sessionId) return
    const activeTabs = new Map(useStageStore.getState().activeTabIdBySession)
    activeTabs.set(sessionId, null)
    useStageStore.setState({ activeTabIdBySession: activeTabs })
  }

  return (
    <div className={collapsed ? 'flex flex-col items-center gap-2' : 'flex flex-nowrap items-center gap-1 overflow-hidden'}>
      {TOOLS.map((tool) => {
        const label = t(`stage.toolRow.${tool.tool}`)
        const comingSoon = t('stage.toolRow.comingSoon')

        if (tool.enabled) {
          return (
            <Button
              key={tool.tool}
              type="button"
              size="xs"
              variant="ghost"
              aria-label={label}
              onClick={() => {
                openOrFocusStageToolTab({
                  getState: useStageStore.getState,
                  sessionId,
                  target: {
                    kind: 'global_tool',
                    tool: 'sql',
                    title: label,
                  },
                })
                focusWorkspaceTabInStage()
              }}
              className="justify-start gap-1.5 px-2 text-xs"
            >
              <tool.icon className="size-3.5" />
              {!collapsed && <span className="truncate">{label}</span>}
            </Button>
          )
        }

        return (
          <Button
            key={tool.tool}
            type="button"
            size="xs"
            variant="ghost"
            disabled
            aria-label={`${label} ${comingSoon}`}
            className="justify-start gap-1.5 px-2 text-xs text-muted-foreground"
          >
            <tool.icon className="size-3.5" />
            {!collapsed && <span className="truncate">{label}</span>}
            {!collapsed && (
              <Badge variant="outline" className="ml-1 h-4 px-1.5 text-[10px]">
                {comingSoon}
              </Badge>
            )}
          </Button>
        )
      })}
    </div>
  )
}
