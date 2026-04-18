import { Component, type ReactNode } from 'react'

export class ToolErrorBoundary extends Component<
  { fallback: ReactNode; children: ReactNode },
  { hasError: boolean }
> {
  state = { hasError: false }

  static getDerivedStateFromError() {
    return { hasError: true }
  }

  componentDidCatch(err: Error) {
    console.error('[ToolErrorBoundary]', err)
  }

  render() {
    return this.state.hasError ? this.props.fallback : this.props.children
  }
}
