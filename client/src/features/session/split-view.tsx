import { PanelGroup, Panel, PanelResizeHandle } from 'react-resizable-panels'
import { MessageStream } from '@/features/chat/components/message-stream'
import { ArtifactTimelineStrip } from '@/features/ontology/components/artifact-timeline-strip'
import { ArtifactCanvas } from '@/features/ontology/components/artifact-canvas'

export function SplitView() {
  return (
    <PanelGroup direction="horizontal" className="h-full">
      <Panel defaultSize={48} minSize={25}>
        <div className="flex h-full flex-col">
          <div className="flex-1 overflow-y-auto p-4"><MessageStream /></div>
          <div id="composer-slot" />
        </div>
      </Panel>
      <PanelResizeHandle className="w-px bg-border hover:bg-primary/50" />
      <Panel defaultSize={52} minSize={25}>
        <div className="flex h-full flex-col">
          <ArtifactTimelineStrip />
          <div className="flex-1 overflow-hidden"><ArtifactCanvas /></div>
        </div>
      </Panel>
    </PanelGroup>
  )
}
