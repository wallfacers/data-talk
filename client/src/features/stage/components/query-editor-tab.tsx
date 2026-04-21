import type { StageTab } from '@/stores/stage-store'
import { SqlWorkbenchTab } from './sql-workbench-tab'

export function QueryEditorTab({ tab }: { tab: StageTab }) {
  return <SqlWorkbenchTab tab={tab} />
}
