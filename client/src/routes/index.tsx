import { createFileRoute } from '@tanstack/react-router'
import { WorkspaceLayout } from '@/layouts/workspace-layout'

export const Route = createFileRoute('/')({
  component: WorkspaceLayout,
})
