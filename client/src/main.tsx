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
import './styles/globals.css'

registerBuiltInRenderers()
installMonacoLocaleSync()

// Start stage tab persistence hydration (non-blocking)
startStagePersistence().catch(() => {
  // Hydration failure is non-fatal — the coordinator enters degraded mode
  // and the app continues with in-memory-only tab state.
})

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
