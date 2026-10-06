import { z } from 'zod'

export const apiErrorSchema = z.object({ code: z.string(), message: z.string().optional() })

export const documentSummarySchema = z.object({ fileName: z.string(), completed: z.boolean() })

export const previewPageSchema = z.object({ number: z.number(), width: z.number(), height: z.number() })

export const boxSchema = z.object({ page: z.number(), x: z.number(), y: z.number(), width: z.number(), height: z.number() })

export const previewSchema = z.object({
  pages: z.array(previewPageSchema),
  hotspots: z.array(boxSchema.extend({ fieldId: z.string(), marker: z.string().optional() })),
})
