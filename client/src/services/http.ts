import ky, { type BeforeErrorHook, type KyInstance } from 'ky'
import { HTTPError } from 'ky'
import { API_PREFIX, getApiBaseUrl } from './api-prefix'
import { getCurrentLanguage } from '@/stores/ui-settings-store'

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

function createHttp(): KyInstance {
  return ky.create({
    prefixUrl: `${getApiBaseUrl()}${API_PREFIX}`,
    timeout: 30_000,
    retry: { limit: 1 },
    hooks: {
      beforeRequest: [
        (request) => {
          request.headers.set('Accept-Language', getCurrentLanguage())
        },
      ],
      beforeError: [errorNormalizer],
    },
  })
}

let currentHttp = createHttp()

/** Recreate the HTTP client after the API base URL has been updated. */
export function reinitializeHttp() {
  currentHttp = createHttp()
}

/**
 * Shared ky HTTP client. Uses a Proxy so callers always hit the latest instance
 * even after `reinitializeHttp()` replaces it during bootstrap.
 */
export const http: KyInstance = new Proxy({} as KyInstance, {
  get(_, prop, receiver) {
    return Reflect.get(currentHttp, prop, receiver)
  },
})

export { HTTPError }
