/**
 * Backend HTTP path prefix under which all REST + channel endpoints are mounted.
 * Paired with `VITE_API_BASE_URL` (origin only, no trailing path) — never
 * pre-concatenate `/api` into the env var or a client's `baseUrl`.
 */
export const API_PREFIX = '/api'
