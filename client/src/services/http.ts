import ky, { type BeforeErrorHook } from 'ky'
import { HTTPError } from 'ky'
import { API_PREFIX } from './api-prefix'

const errorNormalizer: BeforeErrorHook = async (error) => {
  const silent = (error.options as unknown as Record<string, unknown>).silent === true
  ;(error as unknown as Record<string, unknown>).silent = silent

  try {
    const errData = error as unknown as Record<string, unknown>
    if (!errData.data && error.response) {
      const clone = error.response.clone()
      errData.data = await clone.json().catch(() => ({}))
    }
  } catch {
    // ignore parse errors
  }

  const body = (error as unknown as Record<string, unknown>).data as { message?: string } | undefined
  if (body?.message) {
    error.message = body.message
  }

  return error
}

export const http = ky.create({
  prefixUrl: `${import.meta.env.VITE_API_BASE_URL ?? ''}${API_PREFIX}`,
  timeout: 30_000,
  retry: { limit: 1 },
  hooks: {
    beforeError: [errorNormalizer],
  },
})

export function isSilentError(error: unknown): boolean {
  return (error as unknown as Record<string, unknown>)?.silent === true
}

export { HTTPError }
