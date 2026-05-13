import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { CredentialFormPanel } from '../credential-form-panel'

// Mock sonner toast
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

// Mock the mutation hook
const mockMutate = vi.fn()
vi.mock('../hooks/use-credentials-query', () => ({
  useCreateCredentialMutation: () => ({
    mutate: mockMutate,
    isPending: false,
  }),
  useUpdateCredentialMutation: () => ({
    mutate: mockMutate,
    isPending: false,
  }),
}))

function renderPanel() {
  const onCancel = vi.fn()
  const onSaved = vi.fn()
  const result = render(<CredentialFormPanel editing={null} onCancel={onCancel} onSaved={onSaved} />)
  return { ...result, onCancel, onSaved }
}

async function selectScheme(label: string) {
  fireEvent.click(screen.getByRole('combobox', { name: /认证方式/ }))
  const option = await screen.findByRole('option', { name: new RegExp(label) })
  fireEvent.mouseMove(option)
  fireEvent.pointerEnter(option, { pointerType: 'mouse' })
  fireEvent.click(option)
}

describe('CredentialFormPanel', () => {
  beforeEach(() => {
    mockMutate.mockReset()
  })

  it('renders name and scheme fields', () => {
    renderPanel()
    expect(screen.getByTestId('credential-name-input')).toBeInTheDocument()
    expect(screen.getByTestId('credential-scheme-select')).toBeInTheDocument()
  })

  it('shows no secret field for scheme=none', () => {
    renderPanel()
    const passwordInputs = document.querySelectorAll('input[type="password"]')
    expect(passwordInputs).toHaveLength(0)
  })

  it('calls onCancel when cancel button clicked', () => {
    const { onCancel } = renderPanel()
    fireEvent.click(screen.getByRole('button', { name: /取消/ }))
    expect(onCancel).toHaveBeenCalled()
  })

  it('calls mutate with secret=null for scheme=none', () => {
    renderPanel()
    fireEvent.change(screen.getByTestId('credential-name-input'), { target: { value: 'cred-1' } })
    fireEvent.click(screen.getByRole('button', { name: /新建凭据/ }))

    expect(mockMutate).toHaveBeenCalledWith(
      expect.objectContaining({ secret: null, authScheme: 'none', name: 'cred-1' }),
      expect.any(Object),
    )
  })

  it('reveals bearer token field and submits with secret', async () => {
    renderPanel()
    fireEvent.change(screen.getByTestId('credential-name-input'), { target: { value: 'bearer-cred' } })

    await selectScheme('Bearer')

    const tokenInput = document.getElementById('bearer-token') as HTMLInputElement
    expect(tokenInput).toBeInTheDocument()
    fireEvent.change(tokenInput, { target: { value: 'tok-123' } })

    fireEvent.click(screen.getByRole('button', { name: /新建凭据/ }))

    expect(mockMutate).toHaveBeenCalledWith(
      expect.objectContaining({
        authScheme: 'bearer',
        secret: 'tok-123',
        configNonSecret: {},
      }),
      expect.any(Object),
    )
  })

  it('reveals api key fields and submits with config', async () => {
    renderPanel()
    fireEvent.change(screen.getByTestId('credential-name-input'), { target: { value: 'api-cred' } })

    await selectScheme('Header')

    const nameInput = document.getElementById('api-key-name') as HTMLInputElement
    const valueInput = document.getElementById('api-key-value') as HTMLInputElement
    expect(nameInput).toBeInTheDocument()
    expect(valueInput).toBeInTheDocument()

    fireEvent.change(nameInput, { target: { value: 'X-API-Key' } })
    fireEvent.change(valueInput, { target: { value: 'secret-key' } })

    fireEvent.click(screen.getByRole('button', { name: /新建凭据/ }))

    expect(mockMutate).toHaveBeenCalledWith(
      expect.objectContaining({
        authScheme: 'api_key_header',
        configNonSecret: { headerName: 'X-API-Key' },
        secret: 'secret-key',
      }),
      expect.any(Object),
    )
  })
})
