import { useRef, useState } from 'react'
import { ScriptMonacoEditor } from './script-monaco-editor'
import { ScriptConsolePanel } from './script-console-panel'
import { ScriptToolbar } from './script-toolbar'
import { ScriptEnvGuide } from './script-env-guide'
import { useScriptWorkbenchStore } from '@/features/script/stores/script-workbench-store'
import { useScriptExecute } from '@/features/script/hooks/use-script-execute'

interface ScriptEditorTabProps {
  tabId: string
}

export function ScriptEditorTab({ tabId }: ScriptEditorTabProps) {
  const [consoleHeight, setConsoleHeight] = useState(200)
  const [consoleCollapsed, setConsoleCollapsed] = useState(true)
  const containerRef = useRef<HTMLDivElement>(null)
  const tab = useScriptWorkbenchStore((s) => s.tabsById[tabId])
  const { execute, stop, isRunning, canRun, envInfo } = useScriptExecute(tabId)

  if (!tab) return null

  const hasRuntime = envInfo
    ? Boolean(
        (tab.language === 'python' && envInfo.python)
        || (tab.language === 'javascript' && envInfo.node),
      )
    : true // Not checked yet, don't show guide

  return (
    <div className="flex h-full flex-col">
      <ScriptToolbar
        tabId={tabId}
        isRunning={isRunning}
        canRun={canRun && hasRuntime}
        onRun={execute}
        onStop={stop}
        onToggleConsole={() => {
          setConsoleCollapsed(!consoleCollapsed)
        }}
        consoleCollapsed={consoleCollapsed}
      />
      <div ref={containerRef} className="relative flex flex-1 flex-col overflow-hidden">
        <div className="flex-1 overflow-hidden">
          {!hasRuntime && envInfo ? (
            <ScriptEnvGuide language={tab.language} />
          ) : (
            <ScriptMonacoEditor tabId={tabId} />
          )}
        </div>
        {!consoleCollapsed && (
          <>
            <div
              className="h-1 cursor-row-resize bg-border hover:bg-accent-primary transition-colors"
              onMouseDown={(e) => {
                const startY = e.clientY
                const startHeight = consoleHeight
                const onMove = (ev: MouseEvent) => {
                  const delta = startY - ev.clientY
                  setConsoleHeight(Math.max(100, Math.min(startHeight + delta, 600)))
                }
                const onUp = () => {
                  window.removeEventListener('mousemove', onMove)
                  window.removeEventListener('mouseup', onUp)
                }
                window.addEventListener('mousemove', onMove)
                window.addEventListener('mouseup', onUp)
              }}
            />
            <div style={{ height: consoleHeight }} className="overflow-hidden">
              <ScriptConsolePanel tabId={tabId} />
            </div>
          </>
        )}
      </div>
    </div>
  )
}
