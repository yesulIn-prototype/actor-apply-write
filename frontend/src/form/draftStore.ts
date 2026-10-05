import type { ApplicationDraft } from './draftData'
import { DRAFT_LIFETIME, draftSchema } from './draftData'

const DATABASE = 'yesulin-application-drafts'

const STORE = 'drafts'

export type DraftStore = {
  readonly load: (vid: string) => Promise<ApplicationDraft | undefined>
  readonly save: (draft: ApplicationDraft) => Promise<void>
  readonly remove: (vid: string) => Promise<void>
  readonly close: () => void
}

export function openDraftStore(): Promise<DraftStore> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new DOMException('브라우저 저장소를 사용할 수 없습니다', 'NotSupportedError'))

      return
    }

    const request = indexedDB.open(DATABASE, 1)
    let settled = false

    const timeout = window.setTimeout(() => {
      settled = true
      reject(new DOMException('브라우저 저장소가 응답하지 않습니다', 'TimeoutError'))
    }, 3000)

    request.onupgradeneeded = () => { request.result.createObjectStore(STORE, { keyPath: 'vid' }) }

    request.onerror = () => { window.clearTimeout(timeout); reject(request.error) }

    request.onblocked = () => {
      settled = true
      window.clearTimeout(timeout)
      reject(new DOMException('브라우저 저장소가 사용 중입니다', 'InvalidStateError'))
    }

    request.onsuccess = () => {
      window.clearTimeout(timeout)
      const database = request.result

      if (settled) {
        database.close()

        return
      }

      database.onversionchange = () => database.close()
      resolve({
        load: (vid) => load(database, vid),
        save: (draft) => write(database, (store) => store.put(draft)),
        remove: (vid) => write(database, (store) => store.delete(vid)),
        close: () => database.close(),
      })
    }
  })
}

function write(database: IDBDatabase, action: (store: IDBObjectStore) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(STORE, 'readwrite')
    transaction.oncomplete = () => resolve()
    transaction.onabort = () => reject(transaction.error ?? new DOMException('저장 작업이 중단됐습니다', 'AbortError'))
    action(transaction.objectStore(STORE))
  })
}

/** Expired or malformed drafts are pruned on access, including drafts for other notices. */
function load(database: IDBDatabase, vid: string): Promise<ApplicationDraft | undefined> {
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(STORE, 'readwrite')
    const store = transaction.objectStore(STORE)
    const cutoff = Date.now() - DRAFT_LIFETIME
    let found: ApplicationDraft | undefined

    const timeout = window.setTimeout(() => {
      transaction.abort()
      reject(new DOMException('초안 조회가 응답하지 않습니다', 'TimeoutError'))
    }, 3000)

    transaction.oncomplete = () => { window.clearTimeout(timeout); resolve(found) }

    transaction.onabort = () => {
      window.clearTimeout(timeout)
      reject(transaction.error ?? new DOMException('초안 조회가 중단됐습니다', 'AbortError'))
    }

    const request = store.get(vid)
    request.onsuccess = () => {
      const parsed = draftSchema.safeParse(request.result).data
      found = parsed && parsed.updatedAt > cutoff ? parsed : undefined
    }

    const cursor = store.openCursor()
    cursor.onsuccess = () => {
      const row = cursor.result

      if (!row) return

      const parsed = draftSchema.safeParse(row.value).data

      if (!parsed || parsed.updatedAt <= cutoff) row.delete()

      row.continue()
    }
  })
}
