import { QueryClient } from '@tanstack/react-query'
import { normalizeError, showErrorToast } from '@/services/http-error'

const globalErrorHandler = (error: unknown) => {
  const normalized = normalizeError(error)
  showErrorToast(normalized)
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
})

queryClient.getQueryCache().config.onError = globalErrorHandler
queryClient.getMutationCache().config.onError = globalErrorHandler
