import { z } from 'zod'

export const reportDerivativeStatus = z.enum(['processing', 'ready', 'failed'])
export type ReportDerivativeStatus = z.infer<typeof reportDerivativeStatus>

export const reportListItemSchema = z.object({
  id: z.string(),
  workspaceId: z.string(),
  groupId: z.string(),
  version: z.number().int().min(1),
  title: z.string(),
  subtitle: z.string().nullable().optional(),
  templateId: z.string(),
  generatedAt: z.number().int(),
  pdfStatus: reportDerivativeStatus,
  mdStatus: reportDerivativeStatus,
  groupSize: z.number().int().min(1).optional(),
})

export type ReportListItem = z.infer<typeof reportListItemSchema>

export const reportListSchema = z.object({
  items: z.array(reportListItemSchema),
})

export const reportDetailSchema = reportListItemSchema.extend({
  accentColor: z.string(),
  templateVersion: z.string(),
  generatedBySessionId: z.string().nullable().optional(),
  userPrompt: z.string().nullable().optional(),
  pdfFailReason: z.string().nullable().optional(),
  mdFailReason: z.string().nullable().optional(),
  artifactPaths: z.record(z.string(), z.string().nullable()).optional(),
})

export type ReportDetail = z.infer<typeof reportDetailSchema>

export const reportSystemStatusSchema = z.object({
  chromiumReady: z.boolean(),
  fontsReady: z.boolean(),
  skillReady: z.boolean(),
  message: z.string().nullable().optional(),
})

export type ReportSystemStatus = z.infer<typeof reportSystemStatusSchema>
