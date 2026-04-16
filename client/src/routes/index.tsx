import { createFileRoute } from '@tanstack/react-router'
import { HomePage } from '@/features/workspace/home-page'

export const Route = createFileRoute('/')({
  component: HomePage,
})
