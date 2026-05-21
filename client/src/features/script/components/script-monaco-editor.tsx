import { useCallback, useRef, useEffect } from 'react'
import Editor, { type OnMount } from '@monaco-editor/react'
import { useScriptWorkbenchStore } from '@/features/script/stores/script-workbench-store'

interface ScriptMonacoEditorProps {
  tabId: string
}

export function ScriptMonacoEditor({ tabId }: ScriptMonacoEditorProps) {
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null)
  const tab = useScriptWorkbenchStore((s) => s.tabsById[tabId])
  const setScriptText = useScriptWorkbenchStore((s) => s.setScriptText)

  const handleMount: OnMount = useCallback((editor) => {
    editorRef.current = editor
    editor.focus()
  }, [])

  const handleChange = useCallback(
    (value: string | undefined) => {
      if (value !== undefined) {
        setScriptText(tabId, value)
      }
    },
    [tabId, setScriptText],
  )

  // Sync external changes (e.g., AI apply_text_edits)
  useEffect(() => {
    if (!editorRef.current || !tab) return
    const editor = editorRef.current
    const model = editor.getModel()
    if (!model) return
    const currentValue = model.getValue()
    if (currentValue !== tab.scriptText) {
      editor.setValue(tab.scriptText)
    }
  }, [tab?.scriptText, tab?.version])

  if (!tab) return null

  const language = tab.language === 'python' ? 'python' : 'javascript'

  return (
    <Editor
      height="100%"
      language={language}
      value={tab.scriptText}
      onChange={handleChange}
      onMount={handleMount}
      theme="vs-dark"
      options={{
        fontSize: 14,
        lineHeight: 20,
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        automaticLayout: true,
        padding: { top: 12 },
        tabSize: tab.language === 'python' ? 4 : 2,
      }}
    />
  )
}
