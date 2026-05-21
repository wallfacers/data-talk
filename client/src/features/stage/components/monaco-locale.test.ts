import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useUISettingsStore } from '@/stores/ui-settings-store'
import { configureMonacoLocale, getMonacoLocaleReady, installMonacoLocaleSync } from './monaco-locale'

const loaderConfigMock = vi.hoisted(() => vi.fn())

vi.mock('@monaco-editor/react', () => ({
  loader: {
    config: loaderConfigMock,
  },
}))

function clearMonacoNlsGlobals() {
  delete (globalThis as { _VSCODE_NLS_MESSAGES?: unknown })._VSCODE_NLS_MESSAGES
  delete (globalThis as { _VSCODE_NLS_LANGUAGE?: unknown })._VSCODE_NLS_LANGUAGE
}

function getMonacoNlsMessages() {
  return (globalThis as { _VSCODE_NLS_MESSAGES?: unknown })._VSCODE_NLS_MESSAGES
}

function getMonacoNlsLanguage() {
  return (globalThis as { _VSCODE_NLS_LANGUAGE?: unknown })._VSCODE_NLS_LANGUAGE
}

describe('monaco locale setup', () => {
  beforeEach(() => {
    loaderConfigMock.mockClear()
    window.localStorage.removeItem('ui-settings')
    useUISettingsStore.setState({ language: 'zh-CN' })
    clearMonacoNlsGlobals()
  })

  it('keeps Monaco AMD loader on its bundled language path for Simplified Chinese', async () => {
    await configureMonacoLocale('zh-CN')

    expect(loaderConfigMock).toHaveBeenCalledWith({
      'vs/nls': {
        availableLanguages: {
          '*': 'en',
        },
      },
    })
  })

  it('configures Monaco to use bundled English labels for English UI', async () => {
    await configureMonacoLocale('en-US')

    expect(loaderConfigMock).toHaveBeenCalledWith({
      'vs/nls': {
        availableLanguages: {
          '*': 'en',
        },
      },
    })
    expect(getMonacoNlsLanguage()).toBeUndefined()
    expect(getMonacoNlsMessages()).toBeUndefined()
  })

  it('loads Simplified Chinese Monaco messages before the editor initializes', async () => {
    await configureMonacoLocale('zh-CN')

    expect(getMonacoNlsLanguage()).toBe('zh-cn')
    expect(getMonacoNlsMessages()).toEqual(expect.arrayContaining(['复制', '剪切', '粘贴', '选择全部']))
  })

  it('restores cached Simplified Chinese messages after switching through English', async () => {
    await configureMonacoLocale('zh-CN')
    await configureMonacoLocale('en-US')
    await configureMonacoLocale('zh-CN')

    expect(getMonacoNlsLanguage()).toBe('zh-cn')
    expect(getMonacoNlsMessages()).toEqual(expect.arrayContaining(['复制', '剪切', '粘贴', '选择全部']))
  })

  it('keeps Monaco loader locale aligned with the UI language before editor initialization', async () => {
    const unsubscribe = installMonacoLocaleSync()

    try {
      loaderConfigMock.mockClear()
      useUISettingsStore.setState({ language: 'en-US' })

      expect(loaderConfigMock).toHaveBeenCalledWith({
        'vs/nls': {
          availableLanguages: {
            '*': 'en',
          },
        },
      })
      await getMonacoLocaleReady()
      expect(getMonacoNlsLanguage()).toBeUndefined()
      expect(getMonacoNlsMessages()).toBeUndefined()
    } finally {
      unsubscribe()
    }
  })
})
