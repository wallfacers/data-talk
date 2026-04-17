import { GeneralSettingsPanel } from '@/features/model-config/general-panel'

export function GeneralPage() {
  return (
    <div className="max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold">通用</h1>
      <GeneralSettingsPanel />
    </div>
  )
}
