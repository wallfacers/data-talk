import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { PreviewCanvas } from './preview-canvas'
import { PreviewErrorBoundary } from './preview-error-boundary'
import { PreviewSidebar } from './preview-sidebar'
import { seedPreview, teardownPreview } from './preview-script'

export function PreviewPage() {
  // 同步种子：确保第一次渲染时各 store 里已经有 MOCK 数据 + activeSessionId，
  // 避免 "首帧 NOSESS → 异步 seed → HERO" 的跃迁链。
  const seededRef = useRef(false)
  if (!seededRef.current) {
    seedPreview()
    seededRef.current = true
  }

  const [resetToken, setResetToken] = useState(0)

  useEffect(() => {
    return () => teardownPreview()
  }, [])

  // 暴露给 sidebar 的 reset：清 store + 让 canvas 回 HERO
  const resetAll = () => {
    seedPreview()
    setResetToken((x) => x + 1)
  }

  return (
    <SidebarProvider
      style={
        {
          '--sidebar-width': 'calc(var(--spacing) * 65)',
          '--header-height': 'calc(var(--spacing) * 12)',
        } as CSSProperties
      }
    >
      <PreviewSidebar variant="inset" onReset={resetAll} />
      <SidebarInset>
        <div className="relative min-h-0 flex-1">
          <PreviewErrorBoundary>
            <PreviewCanvas resetToken={resetToken} />
          </PreviewErrorBoundary>
          <div className="pointer-events-none absolute bottom-4 right-4 z-30 rounded-full border bg-background/80 px-2 py-1 text-[10px] font-medium text-muted-foreground shadow-sm backdrop-blur">
            PREVIEW
          </div>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
