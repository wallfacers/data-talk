import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ScriptToolbar } from '@/features/script/components/script-toolbar'
import { useScriptWorkbenchStore } from '@/features/script/stores/script-workbench-store'

// --- Hoisted mocks ---

const { mockSetLanguage, mockSetConnectionId } = vi.hoisted(() => ({
  mockSetLanguage: vi.fn(),
  mockSetConnectionId: vi.fn(),
}))

// --- Mocks ---

vi.mock('@/features/script/stores/script-workbench-store', () => ({
  useScriptWorkbenchStore: vi.fn(),
}))

vi.mock('@/features/connection/store', () => ({
  useConnectionStore: vi.fn(),
}))

vi.mock('@/features/connection/hooks/use-connections', () => ({
  useConnections: vi.fn(),
}))

vi.mock('@/components/ui/button', () => ({
  Button: ({ children, className, disabled, variant, size, onClick, ...props }: any) => {
    // Merge props into a native button. For destructive variant, include it as a data attribute.
    const allProps: Record<string, any> = { ...props }
    return (
      <button
        className={className}
        disabled={disabled}
        onClick={onClick}
        data-variant={variant}
        data-size={size}
        {...allProps}
      >
        {children}
      </button>
    )
  },
}))

// Simpler Select mock: renders items inline so they are queryable and clickable
vi.mock('@/components/ui/select', () => ({
  Select: ({ children, defaultValue }: any) => (
    <div data-testid="select" data-default-value={defaultValue}>
      {children}
    </div>
  ),
  SelectTrigger: ({ children, className }: any) => (
    <div data-testid="select-trigger" className={className}>
      {children}
    </div>
  ),
  SelectContent: ({ children }: any) => (
    <div data-testid="select-content">
      {children}
    </div>
  ),
  SelectItem: ({ children, value, onClick }: any) => (
    <div
      data-testid={`select-item-${value}`}
      onClick={onClick}
      role="option"
    >
      {children}
    </div>
  ),
  SelectValue: ({ placeholder }: any) => (
    <span data-testid="select-value">{placeholder}</span>
  ),
}))

import { useConnectionStore } from '@/features/connection/store'
import { useConnections } from '@/features/connection/hooks/use-connections'

// --- Helpers ---

function mockStoreState(overrides: Record<string, any> = {}) {
  const defaultTab = {
    scriptText: 'print("hello")',
    version: 1,
    language: 'python',
    executeStatus: 'idle',
    consoleOutput: [],
    connectionId: 'conn-1',
    currentRunId: null,
    currentToken: null,
    envInfo: { python: null, node: null },
    envChecked: false,
    errorMessage: null,
  }

  const state = {
    tabsById: {
      'tab-1': { ...defaultTab, ...overrides },
    },
    envInfo: { python: '/usr/bin/python3', node: '/usr/bin/node' },
    envChecked: true,
    setLanguage: mockSetLanguage,
    setConnectionId: mockSetConnectionId,
  }

  ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
    if (typeof selector === 'function') {
      return selector(state)
    }
    return state
  })
}

function mockConnectionStore(activeConnectionId: string | null = null) {
  ;(useConnectionStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
    const state = { activeConnectionId, connections: [] }
    if (typeof selector === 'function') {
      return selector(state)
    }
    return state
  })
}

function mockConnectionsHook(connections: Array<{ id: string; name: string }> = []) {
  ;(useConnections as ReturnType<typeof vi.fn>).mockReturnValue({
    data: connections,
    isLoading: false,
  })
}

// --- Tests ---

describe('ScriptToolbar', () => {
  const defaultProps = {
    tabId: 'tab-1',
    isRunning: false,
    canRun: true,
    onRun: vi.fn(),
    onStop: vi.fn(),
    onToggleConsole: vi.fn(),
    consoleCollapsed: false,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockStoreState()
    mockConnectionStore('conn-1')
    mockConnectionsHook([
      { id: 'conn-1', name: 'PostgreSQL Warehouse' },
      { id: 'conn-2', name: 'MySQL Analytics' },
    ])
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('Run / Stop button behavior', () => {
    it('renders Run button and enables it when canRun is true', () => {
      render(<ScriptToolbar {...defaultProps} canRun={true} isRunning={false} />)

      const runButton = screen.getByRole('button', { name: /run/i })
      expect(runButton).toBeInTheDocument()
      expect(runButton).not.toBeDisabled()
    })

    it('renders Run button disabled when canRun is false', () => {
      render(<ScriptToolbar {...defaultProps} canRun={false} isRunning={false} />)

      const runButton = screen.getByRole('button', { name: /run/i })
      expect(runButton).toBeInTheDocument()
      expect(runButton).toBeDisabled()
    })

    it('renders Stop button (with destructive variant) when isRunning is true', () => {
      render(<ScriptToolbar {...defaultProps} isRunning={true} />)

      const stopButton = screen.getByRole('button', { name: /stop/i })
      expect(stopButton).toBeInTheDocument()
      expect(stopButton).toHaveAttribute('data-variant', 'destructive')
    })

    it('hides Run button when isRunning is true', () => {
      render(<ScriptToolbar {...defaultProps} isRunning={true} />)

      expect(screen.queryByRole('button', { name: /run/i })).not.toBeInTheDocument()
    })

    it('hides Stop button when isRunning is false', () => {
      render(<ScriptToolbar {...defaultProps} isRunning={false} />)

      expect(screen.queryByRole('button', { name: /stop/i })).not.toBeInTheDocument()
    })

    it('calls onRun when Run button is clicked', () => {
      const onRun = vi.fn()
      render(<ScriptToolbar {...defaultProps} onRun={onRun} />)

      fireEvent.click(screen.getByRole('button', { name: /run/i }))
      expect(onRun).toHaveBeenCalledTimes(1)
    })

    it('calls onStop when Stop button is clicked', () => {
      const onStop = vi.fn()
      render(<ScriptToolbar {...defaultProps} isRunning={true} onStop={onStop} />)

      fireEvent.click(screen.getByRole('button', { name: /stop/i }))
      expect(onStop).toHaveBeenCalledTimes(1)
    })
  })

  describe('Run button disabled conditions', () => {
    it('disables Run when canRun is false regardless of other state', () => {
      render(<ScriptToolbar {...defaultProps} canRun={false} />)

      const runButton = screen.getByRole('button', { name: /run/i })
      expect(runButton).toBeDisabled()
    })

    it('canRun=true but connectionId is null (handled by parent via canRun prop)', () => {
      // The toolbar receives canRun as a prop, so it just respects the prop.
      // The actual canRun logic is in useScriptExecute. This test verifies
      // the toolbar correctly reflects the prop.
      render(<ScriptToolbar {...defaultProps} canRun={false} />)

      expect(screen.getByRole('button', { name: /run/i })).toBeDisabled()
    })
  })

  describe('console toggle', () => {
    it('renders console toggle button', () => {
      render(<ScriptToolbar {...defaultProps} />)

      const consoleButton = screen.getByRole('button', { name: /console/i })
      expect(consoleButton).toBeInTheDocument()
    })

    it('calls onToggleConsole when console button is clicked', () => {
      const onToggleConsole = vi.fn()
      render(<ScriptToolbar {...defaultProps} onToggleConsole={onToggleConsole} />)

      fireEvent.click(screen.getByRole('button', { name: /console/i }))
      expect(onToggleConsole).toHaveBeenCalledTimes(1)
    })
  })

  describe('language selector', () => {
    it('renders Python option when envInfo.python is available', () => {
      mockStoreState({ language: 'python' })
      // envInfo.python is '/usr/bin/python3' from default mockStoreState
      render(<ScriptToolbar {...defaultProps} />)

      const pythonOption = screen.getByTestId('select-item-python')
      expect(pythonOption).toBeInTheDocument()
      expect(pythonOption).toHaveTextContent('Python')
    })

    it('renders JavaScript option when envInfo.node is available', () => {
      mockStoreState({ language: 'python' })
      // envInfo.node is '/usr/bin/node' from default mockStoreState
      render(<ScriptToolbar {...defaultProps} />)

      const jsOption = screen.getByTestId('select-item-javascript')
      expect(jsOption).toBeInTheDocument()
      expect(jsOption).toHaveTextContent('JavaScript')
    })

    it('shows Python only when node is null', () => {
      const state = {
        tabsById: {
          'tab-1': {
            scriptText: '', version: 1, language: 'python', executeStatus: 'idle',
            consoleOutput: [], connectionId: 'conn-1', currentRunId: null,
            currentToken: null, envInfo: null, envChecked: false, errorMessage: null,
          },
        },
        envInfo: { python: '/usr/bin/python3', node: null },
        envChecked: true,
        setLanguage: mockSetLanguage,
        setConnectionId: mockSetConnectionId,
      }
      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        if (typeof selector === 'function') return selector(state)
        return state
      })

      render(<ScriptToolbar {...defaultProps} />)

      expect(screen.getByTestId('select-item-python')).toBeInTheDocument()
      expect(screen.queryByTestId('select-item-javascript')).not.toBeInTheDocument()
    })

    it('shows JavaScript only when python is null', () => {
      const state = {
        tabsById: {
          'tab-1': {
            scriptText: '', version: 1, language: 'python', executeStatus: 'idle',
            consoleOutput: [], connectionId: 'conn-1', currentRunId: null,
            currentToken: null, envInfo: null, envChecked: false, errorMessage: null,
          },
        },
        envInfo: { python: null, node: '/usr/bin/node' },
        envChecked: true,
        setLanguage: mockSetLanguage,
        setConnectionId: mockSetConnectionId,
      }
      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        if (typeof selector === 'function') return selector(state)
        return state
      })

      render(<ScriptToolbar {...defaultProps} />)

      expect(screen.queryByTestId('select-item-python')).not.toBeInTheDocument()
      expect(screen.getByTestId('select-item-javascript')).toBeInTheDocument()
    })

    it('shows both language options when envInfo is null (pre-check state)', () => {
      const state = {
        tabsById: {
          'tab-1': {
            scriptText: '', version: 1, language: 'python', executeStatus: 'idle',
            consoleOutput: [], connectionId: 'conn-1', currentRunId: null,
            currentToken: null, envInfo: null, envChecked: false, errorMessage: null,
          },
        },
        envInfo: null,
        envChecked: false,
        setLanguage: mockSetLanguage,
        setConnectionId: mockSetConnectionId,
      }
      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        if (typeof selector === 'function') return selector(state)
        return state
      })

      render(<ScriptToolbar {...defaultProps} />)

      // When envInfo is null, both options should be shown
      // The condition is: (!envInfo || envInfo.python) for Python, (!envInfo || envInfo.node) for JS
      expect(screen.getByTestId('select-item-python')).toBeInTheDocument()
      expect(screen.getByTestId('select-item-javascript')).toBeInTheDocument()
    })

    it('calls setLanguage when a language option is clicked', () => {
      render(<ScriptToolbar {...defaultProps} />)

      const jsOption = screen.getByTestId('select-item-javascript')
      fireEvent.click(jsOption)

      expect(mockSetLanguage).toHaveBeenCalledWith('tab-1', 'javascript')
    })
  })

  describe('connection selector', () => {
    it('renders connection options from useConnections', () => {
      render(<ScriptToolbar {...defaultProps} />)

      // Our mock renders select items with testid pattern "select-item-{value}"
      expect(screen.getByTestId('select-item-conn-1')).toBeInTheDocument()
      expect(screen.getByTestId('select-item-conn-2')).toBeInTheDocument()
    })

    it('calls setConnectionId when a connection is selected', () => {
      render(<ScriptToolbar {...defaultProps} />)

      const connOption = screen.getByTestId('select-item-conn-2')
      fireEvent.click(connOption)

      expect(mockSetConnectionId).toHaveBeenCalledWith('tab-1', 'conn-2')
    })

    it('renders connection names in the selector', () => {
      render(<ScriptToolbar {...defaultProps} />)

      expect(screen.getByText('PostgreSQL Warehouse')).toBeInTheDocument()
      expect(screen.getByText('MySQL Analytics')).toBeInTheDocument()
    })

    it('falls back to activeConnectionId when tab has no connectionId', () => {
      mockStoreState({ connectionId: null })
      mockConnectionStore('conn-2')

      render(<ScriptToolbar {...defaultProps} />)

      // The currentConnectionId should be 'conn-2' (from activeConnectionId)
      const select = screen.getAllByTestId('select')
      // The second select is the connection selector
      expect(select[1]).toBeInTheDocument()
      expect(select[1].getAttribute('data-default-value')).toBe('conn-2')
    })
  })

  describe('tab null check', () => {
    it('returns null when tab does not exist', () => {
      // Override store to return empty tabsById
      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        const state = {
          tabsById: {},
          envInfo: { python: null, node: null },
          envChecked: false,
          setLanguage: mockSetLanguage,
          setConnectionId: mockSetConnectionId,
        }
        if (typeof selector === 'function') return selector(state)
        return state
      })

      const { container } = render(<ScriptToolbar {...defaultProps} />)

      // When tab is null, the component returns null, so nothing is rendered
      expect(container.innerHTML).toBe('')
    })
  })
})
