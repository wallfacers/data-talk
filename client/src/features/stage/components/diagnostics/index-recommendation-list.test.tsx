import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { IndexRecommendationList } from './index-recommendation-list'
import { translateMessage } from '@/i18n/messages'
import type { IndexRecommendation } from '@/features/stage/types/diagnostics'

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    setLanguage: vi.fn(),
    t: (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
      translateMessage('en-US', key, values),
  }),
}))

const highRec: IndexRecommendation = {
  table: 'orders',
  columns: ['user_id', 'created_at'],
  indexType: 'BTREE',
  impact: 'HIGH',
  rationale: 'Composite index to eliminate full scan on orders join.',
}

const mediumRec: IndexRecommendation = {
  table: 'products',
  columns: ['category_id'],
  indexType: 'BTREE',
  impact: 'MEDIUM',
  rationale: 'Index on foreign key to improve join performance.',
}

describe('IndexRecommendationList', () => {
  it('renders recommendation with HIGH impact badge', () => {
    render(<IndexRecommendationList recommendations={[highRec]} />)
    expect(screen.getByText('HIGH')).toBeTruthy()
    expect(screen.getByText(/orders\(user_id, created_at\)/)).toBeTruthy()
    expect(screen.getByText('BTREE')).toBeTruthy()
    expect(screen.getByText('Composite index to eliminate full scan on orders join.')).toBeTruthy()
  })

  it('renders empty state when no recommendations', () => {
    render(<IndexRecommendationList recommendations={[]} />)
    expect(screen.getByText('No index recommendations.')).toBeTruthy()
  })

  it('HIGH impact badge has destructive styling', () => {
    render(<IndexRecommendationList recommendations={[highRec]} />)
    const badge = screen.getByText('HIGH')
    expect(badge.className).toContain('bg-status-danger')
  })

  it('MEDIUM impact badge has amber styling', () => {
    render(<IndexRecommendationList recommendations={[mediumRec]} />)
    const badge = screen.getByText('MEDIUM')
    expect(badge.className).toContain('bg-status-warning')
  })

  it('renders multiple recommendations', () => {
    render(<IndexRecommendationList recommendations={[highRec, mediumRec]} />)
    expect(screen.getByText('HIGH')).toBeTruthy()
    expect(screen.getByText('MEDIUM')).toBeTruthy()
    const items = screen.getAllByRole('listitem')
    expect(items).toHaveLength(2)
  })
})
