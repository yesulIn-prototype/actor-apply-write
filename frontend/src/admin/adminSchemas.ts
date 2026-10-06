import { z } from 'zod'
import { previewPageSchema } from '../apiSchemas'

const statusSchema = z.enum(['DRAFT', 'PUBLISHED', 'CLOSED', 'DELETED'])

const countsSchema = z.object({ visitors: z.number(), hwp: z.number(), pdf: z.number(), unidentifiedJobs: z.number() })

export const usageSchema = z.object({ startedAt: z.string(), counts: countsSchema })

export const summaryPageSchema = z.object({
  items: z.array(z.object({
    vid: z.string(), title: z.string(), status: statusSchema, publishedVersion: z.number(), editingVersion: z.number(), usage: countsSchema,
  })),
  page: z.number(), pageSize: z.number(), total: z.number(), totalPages: z.number(),
})

export const detailSchema = z.object({
  vid: z.string(), status: statusSchema, published: z.array(z.number()), editingVersion: z.number(), editingPublished: z.boolean(),
  originalName: z.string(), hasSource: z.boolean(), definition: z.string(), problems: z.array(z.string()), tested: z.boolean(),
  cells: z.array(z.object({ address: z.string(), text: z.string(), row: z.number(), column: z.number(), rowSpan: z.number(), columnSpan: z.number() })),
  standard: z.boolean(),
})

export const layoutSchema = z.object({
  pages: z.array(previewPageSchema),
  cells: z.array(z.object({ address: z.string(), page: z.number(), x: z.number(), y: z.number(), width: z.number(), height: z.number() })),
})

export const restoredSchema = z.object({ restored: z.array(z.string()), skipped: z.array(z.string()) })
