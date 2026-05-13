import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { listCredentials, createCredential, updateCredential, deleteCredential, credentialsKey, type CredentialCreateRequest } from '../api/credential-api'

export function useCredentialsQuery() {
  return useQuery({ queryKey: credentialsKey, queryFn: listCredentials })
}

export function useCreateCredentialMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CredentialCreateRequest) => createCredential(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: credentialsKey }),
  })
}

export function useUpdateCredentialMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: CredentialCreateRequest }) => updateCredential(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: credentialsKey }),
  })
}

export function useDeleteCredentialMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, force }: { id: string; force?: boolean }) => deleteCredential(id, force),
    onSuccess: () => qc.invalidateQueries({ queryKey: credentialsKey }),
  })
}
