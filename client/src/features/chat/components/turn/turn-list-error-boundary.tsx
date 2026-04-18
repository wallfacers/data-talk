import { Component, type ReactNode } from 'react'

export class TurnListErrorBoundary extends Component<
  { children: ReactNode },
  { hasError: boolean; message?: string }
> {
  state = { hasError: false, message: undefined as string | undefined }

  static getDerivedStateFromError(err: Error) {
    return { hasError: true, message: err.message }
  }

  componentDidCatch(err: Error) {
    console.error('[TurnListErrorBoundary]', err)
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="rounded border border-red-500/40 bg-red-50 dark:bg-red-950/20 p-3 text-xs text-red-700 dark:text-red-300">
          本段渲染出错：<span className="font-mono">{this.state.message}</span>
          <button
            onClick={() => this.setState({ hasError: false, message: undefined })}
            className="ml-2 underline"
          >
            重试
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
