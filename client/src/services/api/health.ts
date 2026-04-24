import { http } from '@/services/http'

export type HealthStatus = {
  status: 'ok' | 'degraded'
  timestamp: string
  message: string
  reason: string | null
}

export function getHealth() {
  return http.get('health').json<HealthStatus>()
}
