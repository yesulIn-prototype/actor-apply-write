import { afterEach, expect, test, vi } from 'vitest'
import { apiErrorSchema, documentSummarySchema, previewSchema } from './apiSchemas'
import { fetchForm } from './form/api'
import { summaryPageSchema } from './admin/adminSchemas'
import { loadForm } from './admin/adminApi'

afterEach(() => { vi.unstubAllGlobals() })

test('accepts server error codes without a message and rejects wrong code types', () => {
  expect(apiErrorSchema.parse({ code: 'FORM_CLOSED' })).toEqual({ code: 'FORM_CLOSED' })
  expect(apiErrorSchema.safeParse({ code: 404 }).success).toBe(false)
})

test('checks resumed files and nested preview locations instead of asserting their JSON type', () => {
  expect(documentSummarySchema.parse({ fileName: '지원서.hwp', completed: true }).completed).toBe(true)
  expect(documentSummarySchema.safeParse({ fileName: '지원서.hwp', completed: 'true' }).success).toBe(false)
  expect(previewSchema.parse({ pages: [], hotspots: [] })).toEqual({ pages: [], hotspots: [] })
  expect(previewSchema.safeParse({ pages: [], hotspots: [{ fieldId: 'name', page: 1, x: 'bad', y: 0, width: 1, height: 1 }] }).success).toBe(false)
})

test('rejects malformed public and operator responses at their API boundary', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ vid: '99003', status: 'UNKNOWN', items: [] }))))
  await expect(fetchForm('99003')).rejects.toThrow()
  await expect(loadForm('99003')).rejects.toThrow()
  expect(summaryPageSchema.parse({ items: [], page: 1, pageSize: 20, total: 0, totalPages: 1 }).total).toBe(0)
  expect(summaryPageSchema.safeParse({ items: [], page: '1', pageSize: 20, total: 0, totalPages: 1 }).success).toBe(false)
})
