import { http } from '@/services/http'

export type HealthStatus = {
  status: 'ok' | 'degraded'
  timestamp: string
}

export function getHealth() {
  return http.get('health').json<HealthStatus>()
}
