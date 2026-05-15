import type { ScriptLanguage } from '@/features/script/stores/script-workbench-store'

interface ScriptEnvGuideProps {
  language: ScriptLanguage
}

export function ScriptEnvGuide({ language }: ScriptEnvGuideProps) {
  const isPython = language === 'python'

  return (
    <div className="flex h-full items-center justify-center bg-canvas p-8">
      <div className="max-w-md space-y-4 text-center">
        <div className="text-4xl">{isPython ? '🐍' : '🟢'}</div>
        <h2 className="text-lg font-semibold text-text-strong">
          {isPython ? 'Python' : 'Node.js'} not detected
        </h2>
        <p className="text-sm text-text-muted">
          Install {isPython ? 'Python 3' : 'Node.js'} to run scripts locally.
        </p>
        <div className="space-y-2">
          <a
            href={isPython ? 'https://www.python.org/downloads/' : 'https://nodejs.org/'}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-block rounded-[var(--radius-md)] bg-accent-primary px-4 py-2 text-sm font-medium text-text-inverse transition-opacity hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent-primary disabled:opacity-50"
          >
            Download {isPython ? 'Python' : 'Node.js'}
          </a>
        </div>
      </div>
    </div>
  )
}
