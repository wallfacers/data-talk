import { DatabaseIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore } from '@/stores/stage-store'
import { openOrFocusStageToolTab } from '@/features/stage/utils/open-or-focus-stage-tool-tab'

export function StageToolRow({
  sessionId,
  collapsed = false,
}: {
  sessionId: string | null
  collapsed?: boolean
}) {
  const { t } = useI18n()
  const label = t('stage.toolRow.sql')

  function focusWorkspaceTabInStage() {
    if (!sessionId) return
    const activeTabs = new Map(useStageStore.getState().activeTabIdBySession)
    activeTabs.set(sessionId, null)
    useStageStore.setState({ activeTabIdBySession: activeTabs })
  }

  return (
    <div className={collapsed ? 'flex flex-col items-center gap-2' : 'flex flex-nowrap items-center gap-1 overflow-hidden'}>
      <Button
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
        <DatabaseIcon className="size-3.5" />
        {!collapsed && <span className="truncate">{label}</span>}
      </Button>
    </div>
  )
}
