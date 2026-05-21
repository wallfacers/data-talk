const OPENCODE_DASH_TEMP_TITLE_REGEX = /^New session - /i
const OPENCODE_TIMESTAMP_TEMP_TITLE_REGEX = /^New\s*Session\s*(?:\u00b7|-)\s*\d{4}/i

export function isOpenCodeTemporarySessionTitle(title: string): boolean {
  return OPENCODE_DASH_TEMP_TITLE_REGEX.test(title) || OPENCODE_TIMESTAMP_TEMP_TITLE_REGEX.test(title)
}

export function displaySessionTitle(title: string, fallback: string): string {
  return isOpenCodeTemporarySessionTitle(title) ? fallback : title
}
