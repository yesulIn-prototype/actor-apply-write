import type { Answers, FormItem, PublicForm } from './types'
import { z } from 'zod'

/** Stored records are parsed at the IndexedDB boundary, never trusted by type assertion. */
export const draftSchema = z.object({
  schema: z.literal(1), vid: z.string().regex(/^\d{1,12}$/), formVersion: z.number().int().positive(),
  updatedAt: z.number().finite(), outputName: z.string().nullable(),
  fields: z.array(z.object({ id: z.string(), signature: z.string(), values: z.array(z.string()).readonly() }).readonly()).readonly(),
}).readonly()

export type ApplicationDraft = z.infer<typeof draftSchema>

export const DRAFT_LIFETIME = 7 * 86400000

function signature(item: FormItem): string {
  return JSON.stringify([item.type, item.label, item.columns])
}

export function draftOf(form: PublicForm, answers: Answers, outputName: string | undefined, now: number): ApplicationDraft {
  return {
    schema: 1, vid: form.vid, formVersion: form.version, updatedAt: now, outputName: outputName ?? null,
    fields: form.items.filter((item) => item.type !== 'PHOTO').map((item) => ({
      id: item.id, signature: signature(item), values: [...(answers[item.id] ?? [])],
    })),
  }
}

export function restoreDraft(form: PublicForm, draft: ApplicationDraft) {
  const answers: Record<string, readonly string[]> = {}

  for (const item of form.items) {
    const saved = draft.fields.find((field) => field.id === item.id && field.signature === signature(item))

    if (!saved || item.type === 'PHOTO') continue

    const values = item.type === 'SINGLE' || item.type === 'MULTI'
      ? saved.values.filter((value) => item.options.some((option) => option.id === value))
      : item.type === 'ROWS' ? saved.values.slice(0, item.maxRows * item.columns.length) : saved.values

    answers[item.id] = values
  }

  return { answers, outputName: draft.outputName ?? undefined, changed: draft.formVersion !== form.version }
}
