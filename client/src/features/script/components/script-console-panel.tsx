import { useEffect, useRef } from 'react'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { useScriptWorkbenchStore } from '@/features/script/stores/script-workbench-store'
import '@xterm/xterm/css/xterm.css'

interface ScriptConsolePanelProps {
  tabId: string
}

export function ScriptConsolePanel({ tabId }: ScriptConsolePanelProps) {
  const terminalRef = useRef<HTMLDivElement>(null)
  const termInstance = useRef<Terminal | null>(null)
  const fitAddonRef = useRef<FitAddon | null>(null)
  const consoleOutput = useScriptWorkbenchStore((s) => s.tabsById[tabId]?.consoleOutput ?? [])
  const lastLenRef = useRef(0)

  // Initialize terminal
  useEffect(() => {
    if (!terminalRef.current) return

    const term = new Terminal({
      scrollback: 10000,
      fontSize: 13,
      lineHeight: 1.2,
      theme: {
        background: '#0f1117',
        foreground: '#e5e7eb',
        cursor: '#9ca3af',
        selectionBackground: '#374151',
      },
      allowTransparency: false,
      convertEol: true,
    })

    const fitAddon = new FitAddon()
    term.loadAddon(fitAddon)
    term.open(terminalRef.current)
    fitAddon.fit()

    termInstance.current = term
    fitAddonRef.current = fitAddon

    return () => {
      term.dispose()
      termInstance.current = null
      fitAddonRef.current = null
    }
  }, [])

  // Handle resize
  useEffect(() => {
    if (!fitAddonRef.current) return
    const observer = new ResizeObserver(() => {
      fitAddonRef.current?.fit()
    })
    if (terminalRef.current) observer.observe(terminalRef.current)
    return () => observer.disconnect()
  }, [])

  // Write new output
  useEffect(() => {
    const term = termInstance.current
    if (!term) return

    const newEntries = consoleOutput.slice(lastLenRef.current)
    lastLenRef.current = consoleOutput.length

    for (const entry of newEntries) {
      const prefix = entry.channel === 'stderr' ? '\x1b[31m' : ''
      const suffix = prefix ? '\x1b[0m' : ''
      term.writeln(`${prefix}${entry.text}${suffix}`)
    }
  }, [consoleOutput])

  return <div ref={terminalRef} className="h-full w-full" />
}
