import { loader } from '@monaco-editor/react'
import type { LanguageOption } from '@/i18n/messages'
import { getCurrentLanguage, useUISettingsStore } from '@/stores/ui-settings-store'

type MonacoNlsGlobal = typeof globalThis & {
  _VSCODE_NLS_MESSAGES?: string[]
  _VSCODE_NLS_LANGUAGE?: string
}

type MonacoNlsSnapshot = {
  messages?: string[]
  language?: string
}

let monacoLocaleReady: Promise<void> = Promise.resolve()
let zhCnNlsSnapshot: MonacoNlsSnapshot | null = null
let zhCnNlsLoad: Promise<MonacoNlsSnapshot> | null = null
let activeLanguage: LanguageOption = getCurrentLanguage()
let localeRequestId = 0

function getMonacoNlsGlobal() {
  return globalThis as MonacoNlsGlobal
}

function captureMonacoNlsSnapshot(): MonacoNlsSnapshot {
  const scope = getMonacoNlsGlobal()

  return {
    messages: scope._VSCODE_NLS_MESSAGES ? [...scope._VSCODE_NLS_MESSAGES] : undefined,
    language: scope._VSCODE_NLS_LANGUAGE,
  }
}

function applyMonacoNlsSnapshot(snapshot: MonacoNlsSnapshot) {
  const scope = getMonacoNlsGlobal()

  if (snapshot.messages) {
    scope._VSCODE_NLS_MESSAGES = [...snapshot.messages]
  } else {
    delete scope._VSCODE_NLS_MESSAGES
  }

  if (snapshot.language) {
    scope._VSCODE_NLS_LANGUAGE = snapshot.language
  } else {
    delete scope._VSCODE_NLS_LANGUAGE
  }
}

function clearMonacoNlsMessages() {
  const scope = getMonacoNlsGlobal()
  delete scope._VSCODE_NLS_MESSAGES
  delete scope._VSCODE_NLS_LANGUAGE
}

function applyCurrentMonacoNlsMessages() {
  if (activeLanguage === 'zh-CN' && zhCnNlsSnapshot) {
    applyMonacoNlsSnapshot(zhCnNlsSnapshot)
    return
  }

  clearMonacoNlsMessages()
}

async function getSimplifiedChineseNlsSnapshot() {
  if (zhCnNlsSnapshot) return zhCnNlsSnapshot

  zhCnNlsLoad ??= import('monaco-editor/esm/nls.messages.zh-cn.js')
    .then(() => {
      zhCnNlsSnapshot = captureMonacoNlsSnapshot()
      return zhCnNlsSnapshot
    })
    .catch((error: unknown) => {
      zhCnNlsLoad = null
      throw error
    })

  return zhCnNlsLoad
}

async function loadMonacoNlsMessages(language: LanguageOption, requestId: number) {
  if (language === 'zh-CN') {
    const snapshot = await getSimplifiedChineseNlsSnapshot()
    if (requestId === localeRequestId || activeLanguage === 'zh-CN') {
      applyMonacoNlsSnapshot(snapshot)
    } else {
      applyCurrentMonacoNlsMessages()
    }
    return
  }

  if (requestId === localeRequestId) {
    clearMonacoNlsMessages()
  }
}

export function configureMonacoLocale(language: LanguageOption = getCurrentLanguage()) {
  activeLanguage = language
  const requestId = ++localeRequestId

  loader.config({
    'vs/nls': {
      availableLanguages: {
        '*': 'en',
      },
    },
  })

  monacoLocaleReady = loadMonacoNlsMessages(language, requestId).catch((error: unknown) => {
    console.error('Failed to load Monaco locale messages', error)
  })

  return monacoLocaleReady
}

export function getMonacoLocaleReady() {
  return monacoLocaleReady
}

export function installMonacoLocaleSync() {
  configureMonacoLocale()

  return useUISettingsStore.subscribe((state, previousState) => {
    if (state.language !== previousState.language) {
      configureMonacoLocale(state.language)
    }
  })
}
