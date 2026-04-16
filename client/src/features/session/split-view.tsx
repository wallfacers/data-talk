import { useEffect, useRef } from 'react'
import {
  PanelGroup,
  Panel,
  PanelResizeHandle,
  type ImperativePanelHandle,
} from 'react-resizable-panels'
import { MessageStream } from '@/features/chat/components/message-stream'
import { ArtifactTimelineStrip } from '@/features/ontology/components/artifact-timeline-strip'
import { ArtifactCanvas } from '@/features/ontology/components/artifact-canvas'
import { StageWindow } from '@/features/stage/components/stage-window'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'

export function SplitView() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const open = useStageStore((s) => (sid ? !!s.openBySession.get(sid) : false))
  const stagePanelRef = useRef<ImperativePanelHandle>(null)

  useEffect(() => {
    const p = stagePanelRef.current
    if (!p) return
    if (open && p.isCollapsed()) p.expand()
    if (!open && !p.isCollapsed()) p.collapse()
  }, [open])

  return (
    <PanelGroup direction="horizontal" className="h-full">
      <Panel defaultSize={48} minSize={25}>
        <div className="flex h-full flex-col">
          <div className="flex-1 overflow-y-auto p-4">
            <MessageStream />
          </div>
          <div id="composer-slot" />
        </div>
      </Panel>
      <PanelResizeHandle className="w-px bg-border hover:bg-primary/50" />
      <Panel
        ref={stagePanelRef}
        defaultSize={52}
        minSize={25}
        collapsible
        collapsedSize={0}
        onCollapse={() => sid && useStageStore.getState().syncCollapsed(sid, true)}
        onExpand={() => sid && useStageStore.getState().syncCollapsed(sid, false)}
      >
        {sid && (
          <div
            data-stage-open={open}
            className="h-full w-full p-2 transition-opacity duration-200 data-[stage-open=false]:opacity-0 data-[stage-open=true]:opacity-100"
          >
            <StageWindow sessionId={sid}>
              <ArtifactTimelineStrip />
              <div className="flex-1 min-h-0 overflow-hidden">
                <ArtifactCanvas />
              </div>
            </StageWindow>
          </div>
        )}
      </Panel>
    </PanelGroup>
  )
}
