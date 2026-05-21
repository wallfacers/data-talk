import { http } from '@/services/http'

export interface UserPreferencesDto {
  timezone: string
  dateFormat: string
}

export function fetchPreferences(): Promise<UserPreferencesDto> {
  return http.get('preferences').json<UserPreferencesDto>()
}

export function updatePreferences(patch: Partial<UserPreferencesDto>): Promise<UserPreferencesDto> {
  return http.put('preferences', { json: patch }).json<UserPreferencesDto>()
}
