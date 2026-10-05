import { IDBFactory } from 'fake-indexeddb'
import { beforeEach, expect, test, vi } from 'vitest'
import { z } from 'zod'
import { openDraftStore } from './draftStore'
import { draftOf, restoreDraft } from './draftData'
import type { PublicForm } from './types'

export const draftForm: PublicForm = {
  vid: '99004', version: 1, title: '합성 지원서', sourceName: 'sample.hwp', fileName: '{name}',
  items: [
    { id: 'name', label: '이름', type: 'TEXT', help: '', required: true, multiline: false, maxLength: 30,
      options: [], min: 0, max: 0, columns: [], maxRows: 0 },
    { id: 'photo', label: '사진', type: 'PHOTO', help: '', required: false, multiline: false, maxLength: 0,
      options: [], min: 0, max: 0, columns: [], maxRows: 0 },
  ],
  submission: { email: '', subject: '', deadline: '', note: '' }, pdfFirst: false,
}

beforeEach(() => { vi.stubGlobal('indexedDB', new IDBFactory()) })

test('restores committed text and file name after reopening, excluding photo fields', async () => {
  // given
  const first = await openDraftStore()
  const draft = draftOf(draftForm, { name: [' 작성 중인 이름 '], photo: ['must not persist'] }, '내 지원서', Date.now())
  // when
  await first.save(draft)
  first.close()
  const reopened = await openDraftStore()
  // then
  const saved = await reopened.load(draftForm.vid)
  expect(saved?.fields.map((field) => field.id)).toEqual(['name'])
  expect(saved && restoreDraft(draftForm, saved)).toMatchObject({ answers: { name: [' 작성 중인 이름 '] }, outputName: '내 지원서' })
  expect(await reopened.load('99005')).toBeUndefined()
  reopened.close()
})

test('does not restore a draft seven days after its last edit', async () => {
  // given
  const store = await openDraftStore()
  await store.save(draftOf(draftForm, { name: ['만료 내용'] }, undefined, Date.now() - 7 * 86400000))
  // when / then
  expect(await store.load(draftForm.vid)).toBeUndefined()
  store.close()
})

test('serializes immediate edits and explicit deletion without resurrecting older values', async () => {
  // given
  const store = await openDraftStore()
  const first = store.save(draftOf(draftForm, { name: ['이전'] }, undefined, Date.now()))
  const latest = store.save(draftOf(draftForm, { name: ['최종'] }, undefined, Date.now()))
  // when
  await Promise.all([first, latest, store.remove(draftForm.vid)])
  // then
  expect(await store.load(draftForm.vid)).toBeUndefined()
  store.close()
})

test('restores compatible fields after a version change and drops changed field meanings', () => {
  // given
  const saved = draftOf(draftForm, { name: ['이전 이름'] }, undefined, Date.now())
  // when / then
  expect(restoreDraft({ ...draftForm, version: 2 }, saved).answers).toEqual({ name: ['이전 이름'] })
  expect(restoreDraft({ ...draftForm, items: draftForm.items.map((item) => ({ ...item, label: '다른 질문' })) }, saved).answers).toEqual({})
})

test('round-trips choices and incomplete career rows without trimming or shifting columns', async () => {
  // given
  const base = draftForm.items[0]

  if (!base) throw new Error('합성 항목이 없습니다')

  const form: PublicForm = { ...draftForm, items: [base,
    { ...base, id: 'roles', label: '배역', type: 'MULTI', options: [{ id: 'a', label: '가' }, { id: 'b', label: '나' }] },
    { ...base, id: 'work', label: '경력', type: 'ROWS', columns: [{ id: 'title', label: '작품' }, { id: 'role', label: '배역' }], maxRows: 3 },
  ] }

  const store = await openDraftStore()
  const answers = { name: ['이름'], roles: ['a', 'b'], work: ['첫 작품', '', '', '다음 역할'] }
  // when
  await store.save(draftOf(form, answers, undefined, Date.now()))
  const saved = await store.load(form.vid)
  // then
  expect(saved && restoreDraft(form, saved).answers).toEqual(answers)
  store.close()
})

test('discards malformed persisted records', async () => {
  // given
  const store = await openDraftStore()

  const raw = await new Promise<IDBDatabase>((resolve) => {
    const request = indexedDB.open('yesulin-application-drafts', 1)
    request.onsuccess = () => resolve(request.result)
  })

  await new Promise<void>((resolve) => {
    const transaction = raw.transaction('drafts', 'readwrite')
    transaction.objectStore('drafts').put({ vid: '99004', schema: 99, answers: new Blob(['invalid']) })
    transaction.oncomplete = () => resolve()
  })
  // when / then
  expect(await store.load(draftForm.vid)).toBeUndefined()
  raw.close()
  store.close()
})

test('aborts a stalled lookup so writing can continue without late restoration', async () => {
  // given
  const store = await openDraftStore()
  const expire = vi.fn<() => void>()

  const timer = vi.spyOn(window, 'setTimeout').mockImplementation((handler) => {
    const callback = z.function().parse(handler)
    expire.mockImplementation(() => { callback() })

    return 123
  })

  try {
    // when
    const pending = store.load(draftForm.vid)
    const rejected = expect(pending).rejects.toMatchObject({ name: 'TimeoutError' })
    // then
    expire()
    await rejected
  } finally {
    timer.mockRestore()
    store.close()
  }
})
