import { createFromTemplate } from '../api/semantic-api'
import { useMutation, useQueryClient } from '@tanstack/react-query'

interface EmptyStateProps {
  connectionId: string
}

export function EmptyState({ connectionId }: EmptyStateProps) {
  const queryClient = useQueryClient()

  const templateMutation = useMutation({
    mutationFn: (templateName: string) => createFromTemplate(connectionId, templateName),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['semantic-domains', connectionId] })
    },
  })

  return (
    <div className="flex flex-col items-center justify-center h-64 gap-6 p-8">
      <div className="text-center">
        <h3 className="text-lg font-semibold mb-2">No Semantic Model Defined</h3>
        <p className="text-sm text-muted-foreground mb-4">
          Get started with a built-in template or let AI infer a model from your schema.
        </p>
      </div>
      <div className="flex gap-4">
        <button
          className="px-4 py-2 rounded bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:opacity-50"
          onClick={() => templateMutation.mutate('ecommerce')}
          disabled={templateMutation.isPending}
        >
          Create from Ecommerce Template
        </button>
        <button
          className="px-4 py-2 rounded bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:opacity-50"
          onClick={() => templateMutation.mutate('saas')}
          disabled={templateMutation.isPending}
        >
          Create from SaaS Template
        </button>
        <button
          className="px-4 py-2 rounded border border-blue-600 text-blue-600 text-sm hover:bg-blue-50 disabled:opacity-50"
          disabled
        >
          AI Infer Model (coming soon)
        </button>
      </div>
      {templateMutation.isSuccess && (
        <div className="text-sm text-green-600">
          Template created: {templateMutation.data?.domain}
        </div>
      )}
    </div>
  )
}
