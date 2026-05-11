import { useEffect, useRef, useState } from 'react'
import { fetchDashboardHtml } from './services/dashboard-api'
import { isIframeToHost, type HostToIframe, type IframeToHost } from './iframe-protocol'

export interface DashboardIframeShellProps {
  dashboardId: string
  params?: Record<string, unknown>
  onError?: (e: { widgetId: string; message: string }) => void
}

export function DashboardIframeShell({ dashboardId, params, onError }: DashboardIframeShellProps) {
  const ref = useRef<HTMLIFrameElement>(null)
  const [html, setHtml] = useState<string | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'missing' | 'error'>('loading')
  const [hash, setHash] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setStatus('loading')
    setHtml(null)
    fetchDashboardHtml(dashboardId).then(h => {
      if (!active) return
      if (h === null) setStatus('missing')
      else setHtml(h)
    })
    return () => { active = false }
  }, [dashboardId])

  useEffect(() => {
    function onMsg(ev: MessageEvent) {
      if (!isIframeToHost(ev.data)) return
      const m = ev.data as IframeToHost
      if (m.type === 'ready') { setStatus('ready'); setHash(m.jsonHash) }
      if (m.type === 'error') onError?.({ widgetId: m.widgetId, message: m.message })
    }
    window.addEventListener('message', onMsg)
    return () => window.removeEventListener('message', onMsg)
  }, [onError])

  useEffect(() => {
    if (status !== 'ready' || !params) return
    const msg: HostToIframe = { type: 'params/update', params }
    ref.current?.contentWindow?.postMessage(msg, '*')
  }, [params, status])

  if (status === 'missing') {
    return <div role="status" className="flex items-center justify-center h-full text-[var(--dt-muted-foreground)] p-8">
      <p>v1 dashboard — 在 chat 中说「重新生成视觉」生成新版 HTML</p>
    </div>
  }
  if (html === null) {
    return <div role="status" className="flex items-center justify-center h-full text-[var(--dt-muted-foreground)]">加载中…</div>
  }
  return <iframe
    ref={ref}
    sandbox="allow-scripts"
    srcDoc={html}
    referrerPolicy="no-referrer"
    className="w-full h-full border-0"
    title={`dashboard ${dashboardId}`}
    data-status={status}
    data-json-hash={hash ?? ''}
  />
}
