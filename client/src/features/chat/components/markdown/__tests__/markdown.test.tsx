import { describe, it, expect, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { Markdown } from '../markdown'
import { SQL_EXPLAIN_EVENT, SQL_EXECUTE_EVENT } from '../sql-code-block'

describe('Markdown', () => {
  it('sanitizes script tags', () => {
    const { container } = render(<Markdown text="<script>alert(1)</script>hello" cacheKey="t1" />)
    expect(container.querySelector('script')).toBeNull()
  })

  it('renders headings', async () => {
    render(<Markdown text="# Title" cacheKey="t2" />)
    await screen.findByText('Title')
  })

  it('renders fenced code inside a code window with chrome slots', async () => {
    const { container } = render(
      <Markdown text={'```powershell\n$env:JAVA_HOME="D:/software/java"\n```'} cacheKey="code-1" />,
    )
    await waitFor(() => {
      expect(container.querySelector('[data-component="markdown-code"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="markdown-code-bar"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="markdown-code-language"]')?.textContent).toMatch(
        /PowerShell/i,
      )
      expect(container.querySelector('[data-slot="markdown-copy-button"]')).not.toBeNull()
    })
  })

  it('renders SQL code inside a code window with shared chrome', async () => {
    const { container } = render(<Markdown text={'```sql\nselect 1;\n```'} cacheKey="sql-1" />)
    await waitFor(() => {
      expect(container.querySelector('[data-component="markdown-code"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="markdown-code-bar"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="markdown-code-language"]')?.textContent).toBe('SQL')
      expect(container.querySelector('[data-slot="sql-kind"]')?.textContent).toBe('SELECT')
      expect(container.querySelector('[data-slot="sql-execute"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="sql-explain"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="markdown-copy-button"]')).not.toBeNull()
    })
  })

  it('dispatches SQL execute and explain events from the shared chrome', async () => {
    const onExecute = vi.fn()
    const onExplain = vi.fn()
    window.addEventListener(SQL_EXECUTE_EVENT, onExecute)
    window.addEventListener(SQL_EXPLAIN_EVENT, onExplain)

    const { container } = render(<Markdown text={'```sql\nselect 1;\n```'} cacheKey="sql-2" />)
    await waitFor(() => {
      expect(container.querySelector('[data-slot="sql-execute"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="sql-explain"]')).not.toBeNull()
    })

    fireEvent.click(container.querySelector('[data-slot="sql-execute"]') as HTMLElement)
    fireEvent.click(container.querySelector('[data-slot="sql-explain"]') as HTMLElement)

    expect(onExecute).toHaveBeenCalledTimes(1)
    expect(onExplain).toHaveBeenCalledTimes(1)

    window.removeEventListener(SQL_EXECUTE_EVENT, onExecute)
    window.removeEventListener(SQL_EXPLAIN_EVENT, onExplain)
  })

  it('wraps rendered tables with the unified scroll container', async () => {
    const markdown = '| name | value |\n| --- | --- |\n| JAVA_HOME | graalvm |'
    const { container } = render(<Markdown text={markdown} cacheKey="table-1" />)
    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument())
    await waitFor(() => {
      expect(container.querySelector('[data-component="markdown-table"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="markdown-table-bar"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="markdown-table-copy"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="markdown-table-csv"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="markdown-table-more"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="markdown-table-scroll"] table')).not.toBeNull()
    })
  })

  it('copies table CSV from the action bar', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, {
      clipboard: {
        writeText,
      },
    })

    const markdown = '| name | value |\n| --- | --- |\n| JAVA_HOME | graalvm |'
    const { container } = render(<Markdown text={markdown} cacheKey="table-2" />)
    await waitFor(() => expect(container.querySelector('[data-slot="markdown-table-csv"]')).not.toBeNull())

    fireEvent.click(container.querySelector('[data-slot="markdown-table-csv"]') as HTMLElement)

    await waitFor(() => {
      expect(writeText).toHaveBeenCalledWith('name,value\r\nJAVA_HOME,graalvm')
    })
  })

  it('copies table as html and plain text when rich clipboard support exists', async () => {
    const write = vi.fn().mockResolvedValue(undefined)
    const ClipboardItemMock = vi.fn((items: Record<string, Blob>) => items)
    Object.assign(navigator, {
      clipboard: {
        write,
      },
    })
    vi.stubGlobal('ClipboardItem', ClipboardItemMock)

    const markdown = '| name | value |\n| --- | --- |\n| JAVA_HOME | graalvm |'
    const { container } = render(<Markdown text={markdown} cacheKey="table-3" />)
    await waitFor(() => expect(container.querySelector('[data-slot="markdown-table-copy"]')).not.toBeNull())

    fireEvent.click(container.querySelector('[data-slot="markdown-table-copy"]') as HTMLElement)

    await waitFor(() => {
      expect(write).toHaveBeenCalledTimes(1)
      expect(ClipboardItemMock).toHaveBeenCalledTimes(1)
    })
  })

  it('downloads csv from the more menu', async () => {
    const createObjectURL = vi.fn(() => 'blob:csv')
    const revokeObjectURL = vi.fn()
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.stubGlobal('URL', {
      createObjectURL,
      revokeObjectURL,
    })

    const markdown = '| name | value |\n| --- | --- |\n| JAVA_HOME | graalvm |'
    const { container } = render(<Markdown text={markdown} cacheKey="table-4" />)
    await waitFor(() => expect(container.querySelector('[data-slot="markdown-table-more"]')).not.toBeNull())

    fireEvent.click(container.querySelector('[data-slot="markdown-table-more"]') as HTMLElement)
    fireEvent.click(
      container.querySelector('[data-slot="markdown-table-action"][data-format="download-csv"]') as HTMLElement,
    )

    await waitFor(() => {
      expect(createObjectURL).toHaveBeenCalledTimes(1)
      expect(clickSpy).toHaveBeenCalledTimes(1)
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:csv')
    })

    clickSpy.mockRestore()
  })

  it('copies normalized JSON from the more menu', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, {
      clipboard: {
        writeText,
      },
    })

    const markdown = '| 值 | 值 | |\n| --- | --- | --- |\n| 1 | 2 | 3 |'
    const { container } = render(<Markdown text={markdown} cacheKey="table-5" />)
    await waitFor(() => expect(container.querySelector('[data-slot="markdown-table-more"]')).not.toBeNull())

    fireEvent.click(container.querySelector('[data-slot="markdown-table-more"]') as HTMLElement)
    fireEvent.click(
      container.querySelector('[data-slot="markdown-table-action"][data-format="json"]') as HTMLElement,
    )

    await waitFor(() => {
      expect(writeText).toHaveBeenCalledWith('[{"值":"1","值_2":"2","column_3":"3"}]')
    })
  })
})
