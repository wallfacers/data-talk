import type { useDashboardTabsStore } from '../../../src/features/dashboard/stores/dashboard-tabs-store'

type DashboardStoreState = ReturnType<typeof useDashboardTabsStore.getState>

declare global {
  interface Window {
    __DT_E2E__?: {
      stage: () => unknown
      er: () => unknown
      session: () => unknown
      coordinator: () => unknown
      dashboard: () => DashboardStoreState
    }
  }
}

export {}
