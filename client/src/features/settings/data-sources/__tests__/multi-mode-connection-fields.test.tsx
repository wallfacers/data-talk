import { fireEvent, render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { MultiModeConnectionFields, type CompatibilityMode } from '../multi-mode-connection-fields'

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    t: (key: string) => key,
  }),
}))

function renderMultiMode(props: Partial<Parameters<typeof MultiModeConnectionFields>[0]> = {}) {
  return render(
    <MultiModeConnectionFields
      kind="oceanbase"
      mode="mysql"
      onModeChange={vi.fn()}
      modeOptions={['mysql', 'oracle']}
      modeDisabled={[]}
      {...props}
    />,
  )
}

describe('MultiModeConnectionFields', () => {
  it('renders all mode options as radio buttons', () => {
    renderMultiMode()
    expect(screen.getByRole('radio', { name: 'connection.compatibilityMode.mysql' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'connection.compatibilityMode.oracle' })).toBeInTheDocument()
  })

  it('marks the selected mode as aria-checked', () => {
    renderMultiMode({ mode: 'mysql' })
    const mysql = screen.getByRole('radio', { name: 'connection.compatibilityMode.mysql' })
    const oracle = screen.getByRole('radio', { name: 'connection.compatibilityMode.oracle' })
    expect(mysql).toHaveAttribute('aria-checked', 'true')
    expect(oracle).toHaveAttribute('aria-checked', 'false')
  })

  it('calls onModeChange when clicking an enabled option', () => {
    const onModeChange = vi.fn()
    renderMultiMode({ onModeChange })
    fireEvent.click(screen.getByRole('radio', { name: 'connection.compatibilityMode.oracle' }))
    expect(onModeChange).toHaveBeenCalledWith('oracle')
  })

  it('does not call onModeChange when clicking a disabled option', () => {
    const onModeChange = vi.fn()
    renderMultiMode({
      onModeChange,
      modeDisabled: ['oracle'],
    })
    const oracle = screen.getByRole('radio', { name: 'connection.compatibilityMode.oracle' })
    expect(oracle).toBeDisabled()
    fireEvent.click(oracle)
    expect(onModeChange).not.toHaveBeenCalled()
  })

  it('enabled options are keyboard reachable', () => {
    renderMultiMode({ modeDisabled: [] })
    const mysql = screen.getByRole('radio', { name: 'connection.compatibilityMode.mysql' })
    expect(mysql).not.toHaveAttribute('tabindex', '-1')
  })

  it('disabled options have disabled semantics and are not selectable', () => {
    renderMultiMode({ modeDisabled: ['oracle'] })
    const oracle = screen.getByRole('radio', { name: 'connection.compatibilityMode.oracle' })
    expect(oracle).toHaveAttribute('aria-disabled', 'true')
    expect(oracle).toHaveAttribute('disabled', '')
  })

  it('selected option has a non-color state indicator (inner dot)', () => {
    const { container } = renderMultiMode({ mode: 'mysql' })
    const selectedRadio = screen.getByRole('radio', { name: 'connection.compatibilityMode.mysql' })
    // The inner dot span should exist inside the selected radio
    const innerDot = selectedRadio.querySelector('span.rounded-full.bg-\\[var\\(--color-accent-primary\\)\\]')
    expect(innerDot).toBeInTheDocument()

    // Unselected radio should not have the inner dot
    const unselectedRadio = screen.getByRole('radio', { name: 'connection.compatibilityMode.oracle' })
    const unselectedDot = unselectedRadio.querySelector('span.rounded-full.bg-\\[var\\(--color-accent-primary\\)\\]')
    expect(unselectedDot).not.toBeInTheDocument()
  })

  it('renders compatibility mode label', () => {
    renderMultiMode()
    expect(screen.getByText('connection.compatibilityMode')).toBeInTheDocument()
  })
})
