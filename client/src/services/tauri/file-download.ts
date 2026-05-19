export function isTauriEnvironment(): boolean {
  return '__TAURI_INTERNALS__' in window
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

export async function downloadFromUrl(url: string, filename: string): Promise<void> {
  if (isTauriEnvironment()) {
    const [{ save }, { writeFile }] = await Promise.all([
      import('@tauri-apps/plugin-dialog'),
      import('@tauri-apps/plugin-fs'),
    ])
    const path = await save({ defaultPath: filename })
    if (!path) return
    const res = await fetch(url)
    if (!res.ok) throw new Error(`Download failed: ${res.status}`)
    const buffer = await res.arrayBuffer()
    await writeFile(path, new Uint8Array(buffer))
  } else {
    const res = await fetch(url)
    if (!res.ok) throw new Error(`Download failed: ${res.status}`)
    const blob = await res.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(blobUrl)
  }
}

const FORMAT_EXTENSIONS: Record<string, string> = {
  csv: 'csv',
  json: 'json',
  xlsx: 'xlsx',
  sql_insert: 'sql',
}

export function inferFilename(exportId: string, format: string): string {
  const ext = FORMAT_EXTENSIONS[format] ?? format
  return `export-${exportId}.${ext}`
}
