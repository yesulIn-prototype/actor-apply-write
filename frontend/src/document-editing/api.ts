import type { Completed, Hotspot, PreviewPage } from '../api'
import { downloadName, request } from '../api'

export type Region = { readonly id: string; readonly text: string; readonly boxes: readonly Omit<Hotspot, 'fieldId'>[] }
export type EditView = { readonly revision: string; readonly pages: readonly PreviewPage[]; readonly regions: readonly Region[]; readonly limitation: string }
export type Change = { readonly id: string; readonly text: string; readonly inputStyle: boolean }
export type NumberedRegion = Region & { readonly number: string }

function headers(completed: Completed): Record<string, string> {
  if (!completed.editToken) throw new Error('이 브라우저에서 만든 지원서만 직접 수정할 수 있어요')
  return { 'X-Document-Edit-Token': completed.editToken }
}

/** Parse the new edit boundary rather than passing arbitrary JSON into the editor. */
export function parseEditView(value: unknown): EditView {
  if (typeof value !== 'object' || value === null || !('revision' in value) || typeof value.revision !== 'string'
    || !('pages' in value) || !Array.isArray(value.pages) || !('regions' in value) || !Array.isArray(value.regions)
    || !('limitation' in value) || typeof value.limitation !== 'string') throw new Error('편집 정보를 읽지 못했어요')
  const pages: PreviewPage[] = value.pages.map((page: unknown) => {
    if (typeof page !== 'object' || page === null || !('number' in page) || typeof page.number !== 'number'
      || !('width' in page) || typeof page.width !== 'number' || !('height' in page) || typeof page.height !== 'number') throw new Error('편집 쪽 정보를 읽지 못했어요')
    return { number: page.number, width: page.width, height: page.height }
  })
  const regions: Region[] = value.regions.map((region: unknown) => {
    if (typeof region !== 'object' || region === null || !('id' in region) || typeof region.id !== 'string'
      || !('text' in region) || typeof region.text !== 'string' || !('boxes' in region) || !Array.isArray(region.boxes)) throw new Error('편집 영역을 읽지 못했어요')
    const boxes = region.boxes.map((box: unknown) => {
      if (typeof box !== 'object' || box === null || !('page' in box) || typeof box.page !== 'number'
        || !('x' in box) || typeof box.x !== 'number' || !('y' in box) || typeof box.y !== 'number'
        || !('width' in box) || typeof box.width !== 'number' || !('height' in box) || typeof box.height !== 'number') throw new Error('편집 위치를 읽지 못했어요')
      return { page: box.page, x: box.x, y: box.y, width: box.width, height: box.height }
    })
    return { id: region.id, text: region.text, boxes }
  })
  return { revision: value.revision, pages, regions, limitation: value.limitation }
}

export async function fetchEditing(completed: Completed): Promise<EditView> {
  const response = await request(`/api/documents/${completed.documentId}/editing`, { method: 'GET', headers: headers(completed) })
  return parseEditView(await response.json())
}

export async function editDocument(completed: Completed, revision: string, changes: readonly Change[]): Promise<Completed> {
  const response = await request(`/api/documents/${completed.documentId}/editing`, {
    method: 'POST', headers: { ...headers(completed), 'Content-Type': 'application/json' }, body: JSON.stringify({ revision, changes }),
  })
  const file = new File([await response.blob()], downloadName(response, completed.file.name), { type: 'application/x-hwp' })
  return { ...completed, file, directEdited: true }
}

export function numberRegions(regions: readonly Region[]): readonly NumberedRegion[] {
  const positioned = regions.flatMap((region) => {
    const first = [...region.boxes].sort((a, b) => a.page - b.page || a.y - b.y || a.x - b.x)[0]

    return first ? [{ region, first }] : []
  })
  const sorted = positioned.sort((a, b) => {
    return a.first.page - b.first.page || a.first.y - b.first.y || a.first.x - b.first.x || a.region.id.localeCompare(b.region.id)
  })
  const counts = new Map<number, number>()

  return sorted.map(({ region, first }) => {
    const page = first.page
    const count = (counts.get(page) ?? 0) + 1

    counts.set(page, count)

    return { ...region, number: `${page}-${count}` }
  })
}

export function movedChanges(source: NumberedRegion, target: NumberedRegion, text: string): readonly Change[] {
  const start = source.text.indexOf(text)
  if (!text || start < 0 || source.text.indexOf(text, start + text.length) >= 0 || source.id === target.id) {
    throw new Error('현재 글에서 한 번만 나오는 옮길 글과 서로 다른 받을 번호를 선택해 주세요')
  }
  return [
    { id: source.id, text: (source.text.slice(0, start) + source.text.slice(start + text.length)).trimEnd(), inputStyle: false },
    { id: target.id, text, inputStyle: true },
  ]
}
