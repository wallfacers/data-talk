/**
 * Backend HTTP path prefix under which all REST + channel endpoints are mounted.
 * Paired with `VITE_API_BASE_URL` (origin only, no trailing path) — never
 * pre-concatenate `/api` into the env var or a client's `baseUrl`.
 */
export const API_PREFIX = '/api'

/** Runtime override for the API base URL, set by Tauri before app renders. */
let runtimeBaseUrl: string | null = null

/** Set the backend base URL at runtime (called from bootstrap before React renders). */
export function setRuntimeApiBaseUrl(url: string) {
  runtimeBaseUrl = url.replace(/\/$/, '')
}

/**
 * Resolve the API base URL:
 *  1. Runtime override (set by Tauri production mode)
 *  2. `VITE_API_BASE_URL` env var
 *  3. `''` (same-origin — works with Vite dev proxy)
 */
export function getApiBaseUrl(): string {
  if (runtimeBaseUrl !== null) return runtimeBaseUrl
  const env = (import.meta as any).env?.VITE_API_BASE_URL
  if (typeof env === 'string' && env.length > 0) return env.replace(/\/$/, '')
  return ''
}
