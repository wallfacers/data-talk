import { afterEach, describe, it, expect, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { Markdown } from '../markdown'
import { SQL_EXPLAIN_EVENT } from '../sql-code-block'

const markdownCss = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), '../markdown.css'),
  'utf8',
)

vi.mock('../chart-block', () => ({
  ChartBlock: (props: {
    sourceArtifactId?: string
    blockIndex: number
    messageId: string
    partId?: string
  }) => (
    <div
      data-component="chart-block"
      data-chart-source-artifact-id={props.sourceArtifactId ?? ''}
      data-chart-block-index={String(props.blockIndex)}
      data-chart-message-id={props.messageId}
      data-chart-part-id={props.partId ?? ''}
    />
  ),
}))

afterEach(() => {
  vi.useRealTimers()
})

async function flushMicrotasks() {
  await Promise.resolve()
  await Promise.resolve()
}

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

  it('does not dispatch SQL execute event on click (behavior moved to query editor)', async () => {
    // The old SQL_EXECUTE_EVENT dispatch path has been removed.
    // Clicking "Execute SQL" now opens the query editor directly.
    const onExplain = vi.fn()
    window.addEventListener(SQL_EXPLAIN_EVENT, onExplain)

    const { container } = render(<Markdown text={'```sql\nselect 1;\n```'} cacheKey="sql-2" />)
    await waitFor(() => {
      expect(container.querySelector('[data-slot="sql-execute"]')).not.toBeNull()
      expect(container.querySelector('[data-slot="sql-explain"]')).not.toBeNull()
    })

    fireEvent.click(container.querySelector('[data-slot="sql-execute"]') as HTMLElement)

    // Explain still dispatches event
    fireEvent.click(container.querySelector('[data-slot="sql-explain"]') as HTMLElement)
    expect(onExplain).toHaveBeenCalledTimes(1)

    window.removeEventListener(SQL_EXPLAIN_EVENT, onExplain)
  })

  it('keeps streaming code block chrome mounted while code text grows', async () => {
    const first = '```ts\nconst a = 1'
    const second = '```ts\nconst a = 1\nconst b = 2'
    const { container, rerender } = render(
      <Markdown text={first} streaming cacheKey="stream-code-1" />,
    )

    await waitFor(() => {
      expect(container.querySelector('[data-component="markdown-code"]')).not.toBeNull()
    })

    const shell = container.querySelector('[data-component="markdown-code"]')
    const bar = container.querySelector('[data-slot="markdown-code-bar"]')
    const code = container.querySelector('[data-streaming-code-body="true"]')

    rerender(<Markdown text={second} streaming cacheKey="stream-code-1" />)

    await waitFor(() => {
      const streamingCode = container.querySelector('[data-streaming-code-body="true"]')
      expect(streamingCode).not.toBeNull()
      expect(streamingCode?.textContent).toContain('const b = 2')
    })

    expect(container.querySelector('[data-component="markdown-code"]')).toBe(shell)
    expect(container.querySelector('[data-slot="markdown-code-bar"]')).toBe(bar)
    expect(container.querySelector('[data-streaming-code-body="true"]')).toBe(code)
  })

  it('does not show SQL execute or explain actions for an incomplete streaming SQL fence', async () => {
    const { container } = render(
      <Markdown text={'```sql\nselect 1'} streaming cacheKey="stream-sql-incomplete" />,
    )

    await waitFor(() => {
      expect(container.querySelector('[data-component="markdown-code"]')).not.toBeNull()
    })

    expect(container.querySelector('[data-slot="sql-execute"]')).toBeNull()
    expect(container.querySelector('[data-slot="sql-explain"]')).toBeNull()
    expect(container.querySelector('[data-slot="markdown-copy-button"]')).not.toBeNull()
  })

  it('does not give streaming code blocks an extra pre height over completed code blocks', () => {
    expect(markdownCss).not.toMatch(
      /\[data-component="markdown-code"\]\[data-streaming-code="true"\]\s+pre\s*{[^}]*min-height/s,
    )
  })

  it('reserves one shared code body line for streaming and completed code blocks', () => {
    const codeBodyRule = markdownCss.match(
      /\[data-component="markdown-code"\]\s+pre\s*>\s*code\s*{(?<body>[^}]*)}/s,
    )?.groups?.body ?? ''

    expect(codeBodyRule).toContain('display: block')
    expect(codeBodyRule).toContain('min-height: 1.5em')
    expect(codeBodyRule).toContain('white-space: pre')
  })

  it('pins streaming code wrapping via inline style so a commented line cannot briefly wrap to two lines', async () => {
    const { container } = render(
      <Markdown text={'```ts\nconst a = 1; // 我是张三的注释'} streaming cacheKey="stream-code-nowrap" />,
    )

    await waitFor(() => {
      expect(container.querySelector('[data-streaming-code-body="true"]')).not.toBeNull()
    })

    const body = container.querySelector('[data-streaming-code-body="true"]') as HTMLElement
    const inline = body.getAttribute('style') ?? ''
    // Inline style must win over every cascade path — morphdom's attribute
    // churn during stream-code → live can otherwise leave the code briefly
    // inheriting parent white-space and wrapping mid-line in a narrow chat
    // pane.
    expect(inline).toMatch(/white-space:\s*pre\b/)
    expect(inline).toMatch(/word-break:\s*normal/)
    expect(inline).toMatch(/overflow-wrap:\s*normal/)
    expect(inline).toMatch(/display:\s*block/)
  })

  it('matches marked\'s trailing newline so stream-code → live swap has a zero-diff text node', async () => {
    const openText = '```ts\nconst a = 1'
    const { container } = render(
      <Markdown text={openText} streaming cacheKey="stream-code-trailing" />,
    )

    await waitFor(() => {
      expect(container.querySelector('[data-streaming-code-body="true"]')).not.toBeNull()
    })

    // The streaming code body must already end in a newline so that when
    // the closing fence arrives and marked re-renders the same content
    // with its own trailing \n, the morphdom text diff is a no-op and no
    // line-count jitter can occur.
    const streamingText = container.querySelector('[data-streaming-code-body="true"]')?.textContent
    expect(streamingText?.endsWith('\n')).toBe(true)
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

  it('shows a check icon on the table CSV button after copy and restores the label', async () => {
    vi.useFakeTimers()
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, {
      clipboard: {
        writeText,
      },
    })

    const markdown = '| name | value |\n| --- | --- |\n| JAVA_HOME | graalvm |'
    const { container } = render(<Markdown text={markdown} cacheKey="table-csv-feedback" />)
    expect(container.querySelector('[data-slot="markdown-table-csv"]')).not.toBeNull()

    const button = container.querySelector('[data-slot="markdown-table-csv"]') as HTMLElement
    fireEvent.click(button)
    await flushMicrotasks()

    expect(button).toHaveAttribute('data-copied', 'true')
    expect(button.innerHTML).toContain('lucide-check')

    vi.advanceTimersByTime(2000)

    expect(button).not.toHaveAttribute('data-copied')
    expect(button.textContent).toBe('CSV')
    expect(button.innerHTML).not.toContain('lucide-check')
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

  it('keeps the more menu item visible long enough to show copied feedback', async () => {
    vi.useFakeTimers()
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, {
      clipboard: {
        writeText,
      },
    })

    const markdown = '| 值 | 值 | |\n| --- | --- | --- |\n| 1 | 2 | 3 |'
    const { container } = render(<Markdown text={markdown} cacheKey="table-more-feedback" />)
    expect(container.querySelector('[data-slot="markdown-table-more"]')).not.toBeNull()

    fireEvent.click(container.querySelector('[data-slot="markdown-table-more"]') as HTMLElement)

    const jsonAction = container.querySelector(
      '[data-slot="markdown-table-action"][data-format="json"]',
    ) as HTMLElement
    const menu = container.querySelector('[data-slot="markdown-table-menu"]') as HTMLElement

    expect(menu.hidden).toBe(false)

    fireEvent.click(jsonAction)
    await flushMicrotasks()

    expect(jsonAction).toHaveAttribute('data-copied', 'true')
    expect(jsonAction.innerHTML).toContain('lucide-check')
    expect(menu.hidden).toBe(false)

    vi.advanceTimersByTime(2000)

    expect(jsonAction).not.toHaveAttribute('data-copied')
    expect(jsonAction.textContent).toBe('JSON')
    expect(menu.hidden).toBe(true)
  })

  it('replaces chart fences with chart-block mount points and mounts one root', async () => {
    const text = '```chart\n{"series":[{"type":"bar","data":[1,2,3]}]}\n```'
    const { container, rerender } = render(
      <Markdown text={text} streaming={false} cacheKey="msg-1" messageId="m_1" partId="p_1" />,
    )

    await waitFor(() => {
      expect(container.querySelector('[data-component="markdown-chart"]')).toBeInTheDocument()
      expect(container.querySelector('[data-component="chart-block"]')).toBeInTheDocument()
    })

    rerender(<Markdown text={text} streaming={false} cacheKey="msg-1" messageId="m_1" partId="p_1" />)

    expect(container.querySelectorAll('[data-component="chart-block"]')).toHaveLength(1)
  })

  it('parses chart info-string and passes sourceArtifactId to chart block', async () => {
    const text = '```chart:art_123\n{"series":[]}\n```'
    const { container } = render(
      <Markdown text={text} streaming={false} cacheKey="msg-2" messageId="m_2" partId="p_2" />,
    )

    await waitFor(() => {
      const block = container.querySelector('[data-component="chart-block"]') as HTMLElement | null
      expect(block).not.toBeNull()
      expect(block?.dataset.chartSourceArtifactId).toBe('art_123')
    })
  })
})
