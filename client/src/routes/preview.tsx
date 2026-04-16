import { createFileRoute } from '@tanstack/react-router'
import { PreviewPage } from '@/features/preview/preview-page'

export const Route = createFileRoute('/preview')({
  component: PreviewPage,
})
