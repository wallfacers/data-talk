/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module 'monaco-editor/esm/nls.messages.zh-cn.js'

declare module 'react-grid-layout' {
  import type { ComponentType } from 'react'
  export const WidthProvider: <P>(Comp: ComponentType<P>) => ComponentType<P & { measureBeforeMount?: boolean }>
  export const Responsive: ComponentType<Record<string, unknown>>
}
