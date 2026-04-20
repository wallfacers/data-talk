import type { ReactNode } from 'react'
import { SquareIcon, CopyIcon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { useStageStore } from '@/stores/stage-store'
import { useActiveArtifactTitle } from '../use-active-artifact-title'
import { StageDock } from './stage-dock'
import { StageTabBar } from './stage-tab-bar'
import { useI18n } from '@/i18n/use-i18n'

type Props = {
  sessionId?: string
  children: ReactNode
}

export function StageWindow({ sessionId, children }: Props) {
  const { t } = useI18n()
  const closeStage = useStageStore((s) => s.closeStage)
  const maximized = useStageStore((s) => sessionId ? !!s.maximizedBySession.get(sessionId) : false)
  const toggleMaximized = useStageStore((s) => s.toggleMaximized)
  const { Icon, label } = useActiveArtifactTitle(sessionId ?? '')

  // 状态管理：当前激活的 Tab
  const [activeTabId, setActiveTabId] = useState('1')

  function handleClose() {
    if (sessionId) closeStage(sessionId)
  }

  function handleToggleMaximized() {
    if (sessionId) toggleMaximized(sessionId)
  }

  const mockTabs = [
    { id: '1', title: t('stage.tab.ai'), type: 'artifact' as const },
    { id: '2', title: 'user_orders.sql', type: 'sql' as const },
    { id: '3', title: t('stage.tab.erDemo'), type: 'er' as const },
  ]

  // 根据当前 Tab 渲染不同内容
  const renderContent = () => {
    switch (activeTabId) {
      case '1':
        return children // 渲染原始 AI 内容
      case '2':
        return (
          <div className="flex flex-1 items-center justify-center text-muted-foreground italic">
            {t('stage.placeholder.sql')}
          </div>
        )
      case '3':
        return (
          <div className="flex flex-1 items-center justify-center text-muted-foreground italic">
            {t('stage.placeholder.er')}
          </div>
        )
      default:
        return children
    }
  }

  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-xl bg-background shadow-[0_16px_40px_rgb(0,0,0,0.12)] ring-1 ring-border/50 transition-all duration-200">
      
      {/* 顶部整合区：标题栏与标签栏，完全移除分割线 */}
      <div className="flex flex-col bg-muted/30">
        {/* 标题栏 */}
        <div className="group flex h-10 shrink-0 items-center justify-between select-none">
          <div className="flex items-center gap-2 pl-3 pr-2">
            {Icon ? (
              <Icon className="size-4 text-primary" />
            ) : (
              <div className="size-2 rounded-full bg-primary" />
            )}
            <span className="text-xs font-medium text-foreground/80 tracking-wide">
              {label || t('stage.workspace')}
            </span>
          </div>

          <div className="flex h-full items-center">
            <Button
              type="button"
              variant="ghost"
              className="h-full w-11 rounded-none hover:bg-accent hover:text-accent-foreground focus-visible:ring-0 text-muted-foreground"
              aria-label={maximized ? t('stage.restore') : t('stage.maximize')}
              onClick={handleToggleMaximized}
            >
              {maximized ? (
                <CopyIcon className="size-4 rotate-180" strokeWidth={1.5} />
              ) : (
                <SquareIcon className="size-4" strokeWidth={1.5} />
              )}
            </Button>
            
            <Button
              type="button"
              variant="ghost"
              className="h-full w-11 rounded-none hover:bg-[#e81123] hover:text-white focus-visible:ring-0 text-muted-foreground transition-colors"
              aria-label={t('stage.close')}
              onClick={handleClose}
            >
              <XIcon className="size-4" strokeWidth={1.5} />
            </Button>
          </div>
        </div>

        {/* 标签栏 - 传入 setActiveTabId 使其可切换 */}
        <div onClick={(e) => {
          const target = e.target as HTMLElement;
          const tabId = target.closest('[data-tab-id]')?.getAttribute('data-tab-id');
          if (tabId) setActiveTabId(tabId);
        }}>
          {/* 这里我们暂时通过这种方式模拟切换，后续可以使用真正的 store */}
          <StageTabBar tabs={mockTabs} activeId={activeTabId} />
        </div>
      </div>
      
      {/* 内容区域：坤窗窗体效果 */}
      <div className="flex flex-1 min-h-0 flex-col bg-background relative overflow-hidden">
        {/* 恢复左右边距，保持对齐。缩小上下高度：mt-4 (顶部), mb-[110px] (底部避让) */}
        <div className="mx-3 mt-4 mb-[110px] flex-1 overflow-hidden rounded-xl border border-border/60 bg-card shadow-[0_4px_20px_rgb(0,0,0,0.05),inset_0_1px_3px_rgb(0,0,0,0.02)] flex flex-col relative z-0">
          <div className="flex-1 overflow-auto relative">
            {renderContent()}
          </div>
        </div>
        
        {/* 悬浮工具栏 */}
        <div className="absolute bottom-8 left-1/2 -translate-x-1/2 z-10">
          <StageDock />
        </div>
      </div>
    </div>
  )
}
