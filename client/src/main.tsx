import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider, createRouter } from '@tanstack/react-router'
import { QueryClientProvider } from '@tanstack/react-query'
import { routeTree } from './routeTree.gen'
import { queryClient } from './lib/query-client'
import { registerBuiltInRenderers } from '@/features/chat/components/tools/renderers'
import { installMonacoLocaleSync } from '@/features/stage/components/monaco-locale'
import { startStagePersistence } from '@/features/stage/persistence/stage-persistence-bootstrap'
import { I18nProvider } from '@/i18n/provider'
import { useStageStore } from '@/stores/stage-store'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { useSessionStore } from '@/stores/session-store'
import { coordinator } from '@/features/stage/persistence/stage-persistence-bootstrap'
import { setRuntimeApiBaseUrl } from '@/services/api-prefix'
import { reinitializeHttp } from '@/services/http'
import './styles/globals.css'

registerBuiltInRenderers()
installMonacoLocaleSync()

// Start stage tab persistence hydration (non-blocking)
startStagePersistence().catch(() => {
  // Hydration failure is non-fatal — the coordinator enters degraded mode
  // and the app continues with in-memory-only tab state.
})

import { useDashboardTabsStore } from '@/features/dashboard/stores/dashboard-tabs-store'
import { DashboardBlock } from '@/features/chat/components/markdown/dashboard-block'
import * as React from 'react'
import * as ReactDOM from 'react-dom/client'

// Dev-only: expose Zustand stores to Playwright E2E tests
if (import.meta.env.DEV || import.meta.env.MODE === 'test') {
  ;(window as any).__DT_E2E__ = {
    stage: () => useStageStore.getState(),
    er: () => useErTabsStore.getState(),
    session: () => useSessionStore.getState(),
    coordinator: () => coordinator,
    dashboard: () => useDashboardTabsStore.getState(),
  }
  // Expose React, ReactDOM, DashboardBlock, and I18nProvider for inline component mounting in E2E tests
  ;(window as any).reactForE2E = React
  ;(window as any).reactDOMForE2E = ReactDOM
  ;(window as any).dashboardBlockForE2E = { DashboardBlock }
  ;(window as any).i18nForE2E = { I18nProvider }
}

const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('Root element #root not found')
}

/**
 * In Tauri production mode, the backend is started by Rust on a random port.
 * The Rust side exposes `get_backend_url()` to communicate the port to the
 * frontend. We must call this *before* React renders so that all API calls
 * (via `http` ky client and `ChannelClient`) hit the correct backend.
 */
async function bootstrap() {
  if (typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window) {
    try {
      const { invoke } = await import('@tauri-apps/api/core')
      const backendUrl = await invoke<string>('get_backend_url')
      setRuntimeApiBaseUrl(backendUrl)
      reinitializeHttp()
    } catch (e) {
      console.warn('Failed to get backend URL from Tauri, using default:', e)
    }
  }

  createRoot(rootElement!).render(
    <StrictMode>
      <I18nProvider>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </I18nProvider>
    </StrictMode>,
  )
}

bootstrap()

