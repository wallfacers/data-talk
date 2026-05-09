import { fireEvent, render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { KingbaseConnectionFields } from '../kingbase-connection-fields'

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'connection.compatibilityMode': 'Compatibility Mode',
        'connection.compatibilityMode.pg': 'PostgreSQL',
        'connection.compatibilityMode.oracle': 'Oracle',
        'connection.compatibilityMode.disabled.day3Candidate': 'Day-3 candidate; not supported in Day-1',
        'connection.kind.kingbase.alias_normalized': 'Input "kingbasees" normalized to canonical kind "kingbase".',
      }
      return map[key] ?? key
    },
  }),
}))

describe('KingbaseConnectionFields', () => {
  it('renders MultiModeConnectionFields with pg and oracle options, oracle disabled', () => {
    render(
      <KingbaseConnectionFields
        kindInput="kingbase"
        mode="pg"
        onModeChange={vi.fn()}
      />,
    )

    const pgRadio = screen.getByRole('radio', { name: 'PostgreSQL' })
    expect(pgRadio).toBeInTheDocument()
    expect(pgRadio).toHaveAttribute('aria-checked', 'true')

    const oracleRadio = screen.getByRole('radio', { name: 'Oracle' })
    expect(oracleRadio).toBeInTheDocument()
    expect(oracleRadio).toHaveAttribute('aria-checked', 'false')
    expect(oracleRadio).toHaveAttribute('aria-disabled', 'true')
  })

  it('calls onModeChange when user picks pg', () => {
    const onModeChange = vi.fn()
    render(
      <KingbaseConnectionFields
        kindInput="kingbase"
        mode="oracle"
        onModeChange={onModeChange}
      />,
    )

    fireEvent.click(screen.getByRole('radio', { name: 'PostgreSQL' }))
    expect(onModeChange).toHaveBeenCalledWith('pg')
  })

  it('renders alias hint chip when kindInput is "kingbasees"', () => {
    render(
      <KingbaseConnectionFields
        kindInput="kingbasees"
        mode="pg"
        onModeChange={vi.fn()}
      />,
    )

    expect(
      screen.getByText('Input "kingbasees" normalized to canonical kind "kingbase".'),
    ).toBeInTheDocument()
  })

  it('does not render alias hint when kindInput is "kingbase"', () => {
    render(
      <KingbaseConnectionFields
        kindInput="kingbase"
        mode="pg"
        onModeChange={vi.fn()}
      />,
    )

    expect(
      screen.queryByText('Input "kingbasees" normalized to canonical kind "kingbase".'),
    ).not.toBeInTheDocument()
  })
})
