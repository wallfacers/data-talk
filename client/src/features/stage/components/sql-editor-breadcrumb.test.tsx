import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SqlEditorBreadcrumb } from './sql-editor-breadcrumb'

describe('SqlEditorBreadcrumb', () => {
  it('renders the active connection, database, schema, line, and statement kind', () => {
    render(
      <SqlEditorBreadcrumb
        connectionLabel="Primary Connection"
        database="db_main"
        schema="public"
        line={12}
        kind="SELECT"
      />,
    )

    expect(screen.getByText('Primary Connection')).toBeTruthy()
    expect(screen.getByText('db_main')).toBeTruthy()
    expect(screen.getByText('public')).toBeTruthy()
    expect(screen.getByText('Ln 12')).toBeTruthy()
    expect(screen.getByText('SELECT')).toBeTruthy()
  })
})
