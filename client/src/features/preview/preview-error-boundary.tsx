import { Component, type ErrorInfo, type ReactNode } from 'react'

type State = { error: Error | null; info: ErrorInfo | null }

export class PreviewErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null, info: null }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    this.setState({ error, info })
    // 把详细栈打到 console 以便定位
    // eslint-disable-next-line no-console
    console.error('[preview-error-boundary]', error, info.componentStack)
  }

  render() {
    if (this.state.error) {
      return (
        <div className="flex h-full flex-col gap-2 overflow-auto bg-background p-6 text-xs">
          <div className="text-sm font-semibold text-destructive">
            预览渲染出错：{this.state.error.message}
          </div>
          <pre className="max-h-64 overflow-auto whitespace-pre-wrap rounded border bg-muted p-3 text-[10px]">
            {this.state.error.stack}
          </pre>
          {this.state.info?.componentStack && (
            <details className="rounded border bg-muted p-3">
              <summary className="cursor-pointer text-[10px] font-medium">
                Component stack
              </summary>
              <pre className="mt-2 whitespace-pre-wrap text-[10px]">
                {this.state.info.componentStack}
              </pre>
            </details>
          )}
        </div>
      )
    }
    return this.props.children
  }
}
