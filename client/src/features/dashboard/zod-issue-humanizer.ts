import type { z } from 'zod'
import type { TranslationFn } from '@/i18n/provider'

export interface HumanizedIssue {
  path: string
  friendly: string
}

export function pathToHuman(path: ReadonlyArray<PropertyKey>): string {
  let out = ''
  for (const seg of path) {
    if (typeof seg === 'number') {
      out += `[${seg}]`
    } else {
      const s = typeof seg === 'string' ? seg : String(seg)
      out += out.length === 0 ? s : `.${s}`
    }
  }
  return out
}

function getValueAtPath(root: unknown, path: ReadonlyArray<PropertyKey>): unknown {
  let cur: unknown = root
  for (const seg of path) {
    if (cur === null || cur === undefined) return undefined
    cur = (cur as Record<PropertyKey, unknown>)[seg]
  }
  return cur
}

const WIDGET_ID_PATTERN = '/^[a-z]+_w_[a-zA-Z0-9_]{4,32}$/'
const DASHBOARD_ID_PATTERN = '/^dash_[a-zA-Z0-9_]{4,}$/'
const PATTERN_ID_PATTERN = '/^[a-z0-9-]+\\.[a-z0-9-]+$/'
const THEME_PATTERN = '/^industry-[a-z-]+$/'

function widgetIdSuffixLength(value: string): number {
  const idx = value.indexOf('_w_')
  return idx >= 0 ? value.length - idx - 3 : value.length
}

export function humanizeZodIssue(
  issue: z.core.$ZodIssue,
  root: unknown,
  t: TranslationFn,
): HumanizedIssue {
  const path = pathToHuman(issue.path)
  const last = issue.path[issue.path.length - 1]
  const lastKey = typeof last === 'string' ? last : ''
  const rawValue = getValueAtPath(root, issue.path)
  const value = typeof rawValue === 'string' ? rawValue : rawValue == null ? '' : String(rawValue)

  if (issue.code === 'invalid_format') {
    const formatIssue = issue as z.core.$ZodIssue & { format?: string; pattern?: string }
    if (formatIssue.format === 'regex' && typeof formatIssue.pattern === 'string') {
      const pattern = formatIssue.pattern
      if (pattern === WIDGET_ID_PATTERN) {
        return {
          path,
          friendly: t('dashboard.errorDetail.widgetIdRegex', {
            value,
            suffixLength: widgetIdSuffixLength(value),
          }),
        }
      }
      if (pattern === DASHBOARD_ID_PATTERN) {
        return { path, friendly: t('dashboard.errorDetail.dashboardIdRegex', { value }) }
      }
      if (pattern === PATTERN_ID_PATTERN) {
        return { path, friendly: t('dashboard.errorDetail.patternIdRegex', { value }) }
      }
      if (pattern === THEME_PATTERN) {
        return { path, friendly: t('dashboard.errorDetail.themeRegex', { value }) }
      }
    }
  }

  if (
    issue.code === 'invalid_type' &&
    (lastKey === 'id' || lastKey === 'theme' || lastKey === 'patternId')
  ) {
    const typeIssue = issue as z.core.$ZodIssue & { expected?: string }
    return {
      path,
      friendly: t('dashboard.errorDetail.fieldTypeMismatch', {
        field: lastKey,
        expected: typeIssue.expected ?? 'string',
      }),
    }
  }

  return { path, friendly: issue.message }
}
