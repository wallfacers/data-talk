import { Component, type ReactNode } from 'react'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'

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
          {translateMessage(getCurrentLanguage(), 'chat.renderError', { message: this.state.message ?? '' }).replace(this.state.message ?? '', '')}<span className="font-mono">{this.state.message}</span>
          <button
            onClick={() => this.setState({ hasError: false, message: undefined })}
            className="ml-2 underline"
          >
            {translateMessage(getCurrentLanguage(), 'common.retry')}
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
