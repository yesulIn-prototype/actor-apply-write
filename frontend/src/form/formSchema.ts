import { z } from 'zod'

export const publicFormSchema = z.object({
  vid: z.string(), version: z.number(), title: z.string(), fileName: z.string(), sourceName: z.string(),
  items: z.array(z.object({
    id: z.string(), label: z.string(), help: z.string(), required: z.boolean(),
    type: z.enum(['TEXT', 'PHONE', 'SINGLE', 'MULTI', 'PHOTO', 'ROWS']),
    multiline: z.boolean(), maxLength: z.number(), min: z.number(), max: z.number(), maxRows: z.number(),
    options: z.array(z.object({ id: z.string(), label: z.string(), output: z.string().optional() })),
    columns: z.array(z.object({ id: z.string(), label: z.string() })),
  })),
  submission: z.object({ email: z.string(), subject: z.string(), deadline: z.string(), note: z.string() }),
  pdfFirst: z.boolean(),
})
