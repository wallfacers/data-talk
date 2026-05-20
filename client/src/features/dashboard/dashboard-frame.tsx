import { useEffect, useRef, useState, useCallback } from 'react'
import { fetchDashboardHtml } from './services/dashboard-api'
import { isIframeToHost, type HostToIframe, type IframeToHost } from './iframe-protocol'
import { TabContentLoader } from '@/features/stage/components/tab-content-loader'

export interface DashboardFrameProps {
  dashboardId: string
  version?: number
  params?: Record<string, unknown>
  onError?: (e: { widgetId: string; message: string }) => void
}

export function DashboardFrame({ dashboardId, version, params, onError }: DashboardFrameProps) {
  const ref = useRef<HTMLIFrameElement>(null)
  const [html, setHtml] = useState<string | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'missing' | 'error'>('loading')
  const loadedVersionRef = useRef<number | undefined>(undefined)

  // Full reload when dashboardId or version changes
  useEffect(() => {
    let active = true
    setStatus('loading')
    setHtml(null)
    loadedVersionRef.current = version

    fetchDashboardHtml(dashboardId).then(h => {
      if (!active) return
      if (h === null) {
        setStatus('missing')
      } else {
        setHtml(h)
      }
    })
    return () => { active = false }
  }, [dashboardId, version])

  const handleMessage = useCallback((ev: MessageEvent) => {
    if (!isIframeToHost(ev.data)) return
    const m = ev.data as IframeToHost
    if (m.type === 'ready') {
      setStatus('ready')
    }
    if (m.type === 'error') {
      onError?.({ widgetId: m.widgetId, message: m.message })
    }
  }, [onError])

  useEffect(() => {
    window.addEventListener('message', handleMessage)
    return () => window.removeEventListener('message', handleMessage)
  }, [handleMessage])

  // Send params/update when ready
  useEffect(() => {
    if (status !== 'ready' || !params) return
    const msg: HostToIframe = { type: 'params/update', params }
    ref.current?.contentWindow?.postMessage(msg, '*')
  }, [params, status])

  if (status === 'missing') {
    return (
      <div role="status" className="flex items-center justify-center h-full text-[var(--dt-muted-foreground)] p-8">
        <p>Dashboard not found — try regenerating in chat</p>
      </div>
    )
  }

  return (
    <div className="relative w-full h-full">
      {html !== null && (
        <iframe
          ref={ref}
          sandbox="allow-scripts"
          srcDoc={html}
          referrerPolicy="no-referrer"
          className="w-full h-full border-0"
          title={`dashboard ${dashboardId}`}
          data-status={status}
        />
      )}
      {status !== 'ready' && (
        <div className="absolute inset-0">
          <TabContentLoader />
        </div>
      )}
    </div>
  )
}

/**
 * Send a widget/update postMessage to the iframe for incremental hot updates.
 * Returns false if the iframe is not ready.
 */
export function sendWidgetUpdate(
  iframe: HTMLIFrameElement | null,
  update: { widgetId: string; baseOption: Record<string, unknown>; html?: string },
): boolean {
  if (!iframe?.contentWindow) return false
  const msg: HostToIframe = { type: 'widget/update', ...update }
  iframe.contentWindow.postMessage(msg, '*')
  return true
}
