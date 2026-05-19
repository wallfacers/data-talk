import { useQuery } from '@tanstack/react-query'
import { X } from 'lucide-react'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '@/components/ui/table'
import {
  previewDashboard,
  previewReport,
  previewExport,
  previewSemantic,
  previewUpload,
  type ResourceDirName,
} from '@/services/api/maintenance'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface ResourcePreviewDrawerProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  resourceType: ResourceDirName
  resourceId: string
  connectionId?: string
  resourceName?: string
}

type HtmlPreviewData = { type: 'html'; html: string }
type TablePreviewData = {
  type: 'table'
  data: {
    format: string
    columns: string[]
    rows: unknown[][]
    totalRows: number
    previewRows: number
  }
}
type CodePreviewData = { type: 'code'; code: string; language: string }
type ImagePreviewData = { type: 'image'; url: string }
type UnavailablePreviewData = {
  type: 'unavailable'
  mimeType: string
  sizeBytes: number
  previewable: boolean
  message: string
}

type PreviewData =
  | HtmlPreviewData
  | TablePreviewData
  | CodePreviewData
  | ImagePreviewData
  | UnavailablePreviewData

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function HtmlPreview({ html }: { html: string }) {
  return (
    <iframe
      sandbox="allow-scripts"
      srcDoc={html}
      className="w-full h-full min-h-[500px] border-0"
      title="Preview"
    />
  )
}

function CodePreview({ code, language: _language }: { code: string; language: string }) {
  return (
    <pre className="p-4 bg-neutral-50 dark:bg-neutral-900 rounded-lg overflow-auto max-h-[600px] text-sm font-mono">
      <code>{code}</code>
    </pre>
  )
}

function TablePreview({
  data,
}: {
  data: {
    format: string
    columns: string[]
    rows: unknown[][]
    totalRows: number
    previewRows: number
  }
}) {
  return (
    <div>
      <div className="flex items-center gap-2 mb-3">
        <Badge variant="secondary">{data.format}</Badge>
        <span className="text-sm text-muted-foreground">
          Showing {data.previewRows} of {data.totalRows} rows
        </span>
      </div>
      <div className="overflow-auto max-h-[600px]">
        <Table>
          <TableHeader>
            <TableRow>
              {data.columns.map((col) => (
                <TableHead key={col} className="text-xs">
                  {col}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.rows.map((row, i) => (
              <TableRow key={i}>
                {row.map((cell, j) => (
                  <TableCell key={j} className="text-sm font-mono">
                    {String(cell ?? '')}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}

function ImagePreview({ url }: { url: string }) {
  return (
    <img
      src={url}
      alt="Preview"
      className="max-w-full max-h-[600px] object-contain"
    />
  )
}

// ---------------------------------------------------------------------------
// Skeleton
// ---------------------------------------------------------------------------

function PreviewSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-4 w-1/3" />
      <Skeleton className="h-96 w-full rounded-lg" />
    </div>
  )
}

// ---------------------------------------------------------------------------
// Error
// ---------------------------------------------------------------------------

function PreviewError({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16 text-muted-foreground">
      <X className="size-8 text-destructive/60" />
      <p className="text-sm">{message}</p>
    </div>
  )
}

function PreviewUnavailable({
  mimeType,
  sizeBytes,
  message,
}: {
  mimeType: string
  sizeBytes: number
  message: string
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16 text-muted-foreground">
      <div className="rounded-full bg-muted p-3">
        <X className="size-6" />
      </div>
      <p className="text-sm font-medium">{message}</p>
      <div className="flex items-center gap-2 text-xs">
        <Badge variant="outline">{mimeType}</Badge>
        <span>{(sizeBytes / 1024).toFixed(1)} KB</span>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

export function ResourcePreviewDrawer({
  open,
  onOpenChange,
  resourceType,
  resourceId,
  connectionId,
  resourceName,
}: ResourcePreviewDrawerProps) {
  const previewQuery = useQuery({
    queryKey: ['maintenance', 'preview', resourceType, resourceId],
    queryFn: async (): Promise<PreviewData> => {
      switch (resourceType) {
        case 'dashboards': {
          const res = await previewDashboard(resourceId)
          return { type: 'html', html: await res.text() }
        }
        case 'reports': {
          const res = await previewReport(resourceId)
          return { type: 'html', html: await res.text() }
        }
        case 'exports': {
          const data = await previewExport(resourceId)
          return { type: 'table', data }
        }
        case 'semantic': {
          const res = await previewSemantic(resourceId, connectionId!)
          return {
            type: 'code',
            code: await res.text(),
            language: 'yaml',
          }
        }
        case 'uploads': {
          const res = await previewUpload(resourceId)
          const contentType = res.headers.get('content-type') || ''
          if (contentType.startsWith('text/') || contentType.includes('json')) {
            return {
              type: 'code',
              code: await res.text(),
              language: contentType.includes('json') ? 'json' : 'text',
            }
          }
          if (contentType.startsWith('image/')) {
            return { type: 'image', url: res.url }
          }
          // Not previewable — parse metadata from JSON body
          const meta = (await res.json()) as {
            mimeType: string
            sizeBytes: number
            previewable: boolean
            message: string
          }
          return { type: 'unavailable', ...meta }
        }
        default:
          throw new Error(`Unknown resource type: ${resourceType}`)
      }
    },
    enabled: open && !!resourceId,
    staleTime: 30_000,
    retry: false,
  })

  const sheetWidth =
    previewQuery.data?.type === 'code' ? 'max-w-[480px]' : 'max-w-[720px]'

  const title = resourceName || resourceId

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        className={sheetWidth}
        side="right"
        showCloseButton={true}
      >
        <SheetHeader>
          <SheetTitle className="truncate pr-8">{title}</SheetTitle>
          <SheetDescription>
            {renderTypeLabel(resourceType)}
          </SheetDescription>
        </SheetHeader>

        <div className="flex-1 overflow-y-auto">
          {renderPreviewBody(previewQuery)}
        </div>
      </SheetContent>
    </Sheet>
  )
}

// ---------------------------------------------------------------------------
// Body renderer
// ---------------------------------------------------------------------------

function renderPreviewBody(
  query: ReturnType<
    typeof useQuery<PreviewData>
  >,
) {
  if (query.isLoading) {
    return <PreviewSkeleton />
  }

  if (query.isError) {
    const message =
      query.error instanceof Error
        ? query.error.message
        : 'Failed to load preview'
    return <PreviewError message={message} />
  }

  if (!query.data) {
    return null
  }

  switch (query.data.type) {
    case 'html':
      return <HtmlPreview html={query.data.html} />
    case 'table':
      return <TablePreview data={query.data.data} />
    case 'code':
      return (
        <CodePreview
          code={query.data.code}
          language={query.data.language}
        />
      )
    case 'image':
      return <ImagePreview url={query.data.url} />
    case 'unavailable':
      return (
        <PreviewUnavailable
          mimeType={query.data.mimeType}
          sizeBytes={query.data.sizeBytes}
          message={query.data.message}
        />
      )
  }
}

function renderTypeLabel(resourceType: ResourceDirName): string {
  switch (resourceType) {
    case 'dashboards':
      return 'Dashboard'
    case 'reports':
      return 'Report'
    case 'exports':
      return 'Export'
    case 'semantic':
      return 'Semantic Model'
    case 'uploads':
      return 'File Upload'
    default:
      return resourceType
  }
}
