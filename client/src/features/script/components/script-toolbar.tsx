import { Play, Square, ChevronDown, ChevronUp } from 'lucide-react'
import { useScriptWorkbenchStore } from '@/features/script/stores/script-workbench-store'
import { useConnectionStore } from '@/features/connection/store'
import { useConnections } from '@/features/connection/hooks/use-connections'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

interface ScriptToolbarProps {
  tabId: string
  isRunning: boolean
  canRun: boolean
  onRun: () => void
  onStop: () => void
  onToggleConsole: () => void
  consoleCollapsed: boolean
}

export function ScriptToolbar({
  tabId,
  isRunning,
  canRun,
  onRun,
  onStop,
  onToggleConsole,
  consoleCollapsed,
}: ScriptToolbarProps) {
  const tab = useScriptWorkbenchStore((s) => s.tabsById[tabId])
  const setLanguage = useScriptWorkbenchStore((s) => s.setLanguage)
  const setConnectionId = useScriptWorkbenchStore((s) => s.setConnectionId)
  const envInfo = useScriptWorkbenchStore((s) => s.envInfo)
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const { data: connections } = useConnections()

  if (!tab) return null

  const currentConnectionId = tab.connectionId ?? activeConnectionId ?? ''

  return (
    <div className="flex items-center gap-2 border-b border-border-subtle px-3 py-1.5 bg-soft">
      <Select defaultValue={tab.language}>
        <SelectTrigger className="h-7 w-28 text-xs">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {(!envInfo || envInfo.python) && (
            <SelectItem value="python" onClick={() => setLanguage(tabId, 'python')}>
              Python
            </SelectItem>
          )}
          {(!envInfo || envInfo.node) && (
            <SelectItem value="javascript" onClick={() => setLanguage(tabId, 'javascript')}>
              JavaScript
            </SelectItem>
          )}
        </SelectContent>
      </Select>

      <Select defaultValue={currentConnectionId || undefined}>
        <SelectTrigger className="h-7 w-40 text-xs">
          <SelectValue placeholder="Select connection" />
        </SelectTrigger>
        <SelectContent>
          {connections?.map((conn) => (
            <SelectItem
              key={conn.id}
              value={conn.id}
              onClick={() => setConnectionId(tabId, conn.id)}
            >
              {conn.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <div className="flex-1" />

      <Button
        variant="ghost"
        size="sm"
        className="h-7 px-2 text-xs"
        onClick={onToggleConsole}
      >
        {consoleCollapsed ? (
          <ChevronDown className="mr-1 h-3 w-3" />
        ) : (
          <ChevronUp className="mr-1 h-3 w-3" />
        )}
        Console
      </Button>

      {isRunning ? (
        <Button
          variant="destructive"
          size="sm"
          className="h-7 px-3 text-xs"
          onClick={onStop}
        >
          <Square className="mr-1 h-3 w-3" />
          Stop
        </Button>
      ) : (
        <Button
          size="sm"
          className="h-7 px-3 text-xs"
          onClick={onRun}
          disabled={!canRun}
        >
          <Play className="mr-1 h-3 w-3" />
          Run
        </Button>
      )}
    </div>
  )
}
