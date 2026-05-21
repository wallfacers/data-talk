import { useEffect, useRef, useState } from 'react'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { useScriptWorkbenchStore } from '@/features/script/stores/script-workbench-store'
import { useThemeStore } from '@/stores/theme-store'
import '@xterm/xterm/css/xterm.css'

const SYSTEM_MEDIA_QUERY = '(prefers-color-scheme: dark)'

const DARK_TERMINAL_THEME = {
  background: '#1a1a19',
  foreground: '#f1f1ef',
  cursor: '#b9b9b7',
  selectionBackground: '#34322d',
  black: '#34322d',
  red: '#ef4444',
  green: '#22c55e',
  yellow: '#f59e0b',
  blue: '#3b82f6',
  magenta: '#d946ef',
  cyan: '#0ea5e9',
  white: '#f1f1ef',
  brightBlack: '#5e5e5b',
  brightRed: '#f87171',
  brightGreen: '#4ade80',
  brightYellow: '#fbbf24',
  brightBlue: '#60a5fa',
  brightMagenta: '#e879f9',
  brightCyan: '#38bdf8',
  brightWhite: '#fcfcfb',
}

const LIGHT_TERMINAL_THEME = {
  background: '#fcfcfb',
  foreground: '#34322d',
  cursor: '#858481',
  selectionBackground: '#f1f1ef',
  black: '#d1d1cd',
  red: '#b91c1c',
  green: '#15803d',
  yellow: '#b45309',
  blue: '#1d4ed8',
  magenta: '#a21caf',
  cyan: '#0369a1',
  white: '#34322d',
  brightBlack: '#858481',
  brightRed: '#dc2626',
  brightGreen: '#16a34a',
  brightYellow: '#d97706',
  brightBlue: '#2563eb',
  brightMagenta: '#c026d3',
  brightCyan: '#0284c7',
  brightWhite: '#1a1a19',
}

function resolveSystemTheme(): boolean {
  if (typeof window === 'undefined') return false
  return window.matchMedia(SYSTEM_MEDIA_QUERY).matches
}

interface ScriptConsolePanelProps {
  tabId: string
}

export function ScriptConsolePanel({ tabId }: ScriptConsolePanelProps) {
  const terminalRef = useRef<HTMLDivElement>(null)
  const termInstance = useRef<Terminal | null>(null)
  const fitAddonRef = useRef<FitAddon | null>(null)
  const consoleOutput = useScriptWorkbenchStore((s) => s.tabsById[tabId]?.consoleOutput ?? [])
  const lastLenRef = useRef(0)

  const themePreference = useThemeStore((s) => s.theme)
  const [systemPrefersDark, setSystemPrefersDark] = useState(resolveSystemTheme)

  // Track system theme changes when preference is 'system'
  useEffect(() => {
    if (themePreference !== 'system') return
    const mediaQuery = window.matchMedia(SYSTEM_MEDIA_QUERY)
    const handleChange = (event: MediaQueryListEvent) => {
      setSystemPrefersDark(event.matches)
    }
    setSystemPrefersDark(mediaQuery.matches)
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [themePreference])

  // Resolve effective theme
  const isDark = themePreference === 'dark' || (themePreference === 'system' && systemPrefersDark)
  const terminalTheme = isDark ? DARK_TERMINAL_THEME : LIGHT_TERMINAL_THEME

  // Initialize terminal (once)
  useEffect(() => {
    if (!terminalRef.current) return

    const term = new Terminal({
      scrollback: 10000,
      fontSize: 13,
      lineHeight: 1.2,
      theme: terminalTheme,
      allowTransparency: false,
      convertEol: true,
      fontFamily: "'JetBrains Mono', 'SFMono-Regular', 'Cascadia Mono', monospace",
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Update theme when it changes (no dispose + recreate, preserves scrollback)
  useEffect(() => {
    const term = termInstance.current
    if (!term) return
    term.options.theme = terminalTheme
  }, [terminalTheme])

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
