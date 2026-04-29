// Ported from open-db-studio/src/components/ERDesigner/ERCanvas/EREdge.tsx
// (buildSelfRefPath, LOOP_GAP, LOOP_PADDING).

const LOOP_GAP = 40
const LOOP_PADDING = 25

export function buildSelfRefPath(
  sourceX: number,
  sourceY: number,
  targetX: number,
  targetY: number,
  nodeTopY: number,
  nodeBottomY: number,
): [string, number, number] {
  const midX = (sourceX + targetX) / 2
  const handleMidY = (sourceY + targetY) / 2
  const nodeMidY = (nodeTopY + nodeBottomY) / 2
  const goAbove = handleMidY >= nodeMidY
  const loopY = goAbove ? nodeTopY - LOOP_PADDING : nodeBottomY + LOOP_PADDING
  const rightX = sourceX + LOOP_GAP
  const leftX = targetX - LOOP_GAP
  const path = [
    `M ${sourceX},${sourceY}`,
    `L ${rightX},${sourceY}`,
    `L ${rightX},${loopY}`,
    `L ${leftX},${loopY}`,
    `L ${leftX},${targetY}`,
    `L ${targetX},${targetY}`,
  ].join(' ')

  return [path, midX, loopY]
}
