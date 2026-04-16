import type { Artifact } from '@/services/channel/event-reducer'
import { TableArtifact } from './table-artifact'
import { ChartArtifact } from './chart-artifact'
import { ErdArtifact } from './erd-artifact'

export function ArtifactDispatcher({ artifact }: { artifact: Artifact }) {
  switch (artifact.kind) {
    case 'table': return <TableArtifact artifact={artifact} />
    case 'chart': return <ChartArtifact artifact={artifact} />
    case 'erd':   return <ErdArtifact artifact={artifact} />
  }
}
