/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module 'monaco-editor/esm/nls.messages.zh-cn.js'

declare module 'react-grid-layout' {
  import type { ComponentType, RefObject } from 'react'
  export const Responsive: ComponentType<Record<string, unknown>>
  export function useContainerWidth(options?: { measureBeforeMount?: boolean; initialWidth?: number }): {
    width: number
    mounted: boolean
    containerRef: RefObject<HTMLDivElement | null>
  }
}
