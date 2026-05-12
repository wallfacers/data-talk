import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { CredentialForm } from '../credential-form'

describe('CredentialForm', () => {
  it('renders no secret field for scheme=none', () => {
    render(<CredentialForm onSubmit={() => {}} />)
    expect(screen.queryByDisplayValue('')).toBeInTheDocument()
    // No password inputs visible
    const passwordInputs = document.querySelectorAll('input[type="password"]')
    expect(passwordInputs).toHaveLength(0)
  })

  it('reveals bearer token field when scheme=bearer', () => {
    render(<CredentialForm onSubmit={() => {}} />)
    fireEvent.click(screen.getByLabelText(/Bearer Token/))
    const tokenInput = document.getElementById('bearer-token')
    expect(tokenInput).toBeInTheDocument()
  })

  it('reveals api key fields when scheme=api_key_header', () => {
    render(<CredentialForm onSubmit={() => {}} />)
    fireEvent.click(screen.getByLabelText(/API Key \(Header\)/))
    expect(document.getElementById('api-key-name')).toBeInTheDocument()
    expect(document.getElementById('api-key-value')).toBeInTheDocument()
  })

  it('reveals basic auth fields when scheme=basic', () => {
    render(<CredentialForm onSubmit={() => {}} />)
    fireEvent.click(screen.getByLabelText(/Basic Auth/))
    expect(document.getElementById('basic-user')).toBeInTheDocument()
    expect(document.getElementById('basic-pass')).toBeInTheDocument()
  })

  it('submits payload with secret=null for scheme=none', () => {
    const onSubmit = vi.fn()
    render(<CredentialForm onSubmit={onSubmit} />)

    fireEvent.change(screen.getByLabelText(/凭据名/), { target: { value: 'cred-1' } })
    fireEvent.click(screen.getByRole('button', { name: /新建凭据/ }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ secret: null, authScheme: 'none', name: 'cred-1' }),
    )
  })

  it('submits payload with secret for scheme=bearer', () => {
    const onSubmit = vi.fn()
    render(<CredentialForm onSubmit={onSubmit} />)

    fireEvent.change(screen.getByLabelText(/凭据名/), { target: { value: 'bearer-cred' } })
    fireEvent.click(screen.getByLabelText(/Bearer Token/))
    const tokenInput = document.getElementById('bearer-token') as HTMLInputElement
    fireEvent.change(tokenInput, { target: { value: 'tok-123' } })
    fireEvent.click(screen.getByRole('button', { name: /新建凭据/ }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        authScheme: 'bearer',
        secret: 'tok-123',
        configNonSecret: {},
      }),
    )
  })

  it('submits payload with api key config for scheme=api_key_header', () => {
    const onSubmit = vi.fn()
    render(<CredentialForm onSubmit={onSubmit} />)

    fireEvent.change(screen.getByLabelText(/凭据名/), { target: { value: 'api-cred' } })
    fireEvent.click(screen.getByLabelText(/API Key \(Header\)/))
    const nameInput = document.getElementById('api-key-name') as HTMLInputElement
    const valueInput = document.getElementById('api-key-value') as HTMLInputElement
    fireEvent.change(nameInput, { target: { value: 'X-API-Key' } })
    fireEvent.change(valueInput, { target: { value: 'secret-key' } })
    fireEvent.click(screen.getByRole('button', { name: /新建凭据/ }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        authScheme: 'api_key_header',
        configNonSecret: { headerName: 'X-API-Key' },
        secret: 'secret-key',
      }),
    )
  })
})
