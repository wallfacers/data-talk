type Props = {
  title: string
  kind: 'er' | 'report' | 'dashboard' | 'unsupported'
  description: string
  details?: string
}

export function StagePlaceholderTab(_: Props) {
  return null
}
