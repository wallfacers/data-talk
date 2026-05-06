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
import './styles/globals.css'

registerBuiltInRenderers()
installMonacoLocaleSync()

// Start stage tab persistence hydration (non-blocking)
startStagePersistence().catch(() => {
  // Hydration failure is non-fatal — the coordinator enters degraded mode
  // and the app continues with in-memory-only tab state.
})

// Dev-only: expose Zustand stores to Playwright E2E tests
if (import.meta.env.DEV || import.meta.env.MODE === 'test') {
  ;(window as any).__DT_E2E__ = {
    stage: () => useStageStore.getState(),
    er: () => useErTabsStore.getState(),
    session: () => useSessionStore.getState(),
  }
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

createRoot(rootElement).render(
  <StrictMode>
    <I18nProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </I18nProvider>
  </StrictMode>,
)
