const MARKERS = [
  { id: 'er-edge-arrow-default', fill: 'var(--dt-border-strong)' },
  { id: 'er-edge-arrow-virtual', fill: 'var(--dt-accent-warn)' },
  { id: 'er-edge-arrow-selected', fill: 'var(--dt-accent-primary)' },
]

export function ErEdgeMarkers() {
  return (
    <svg width="0" height="0" aria-hidden="true" style={{ position: 'absolute' }}>
      <defs>
        {MARKERS.map(({ id, fill }) => (
          <marker
            key={id}
            id={id}
            viewBox="0 0 8 8"
            refX="7"
            refY="4"
            markerWidth="8"
            markerHeight="8"
            orient="auto-start-reverse"
            markerUnits="userSpaceOnUse"
          >
            <path d="M 0 0 L 8 4 L 0 8 z" fill={fill} />
          </marker>
        ))}
      </defs>
    </svg>
  )
}
