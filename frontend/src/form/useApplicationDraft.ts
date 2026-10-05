import { useEffect, useRef, useState } from 'react'
import type { Answers, PublicForm } from './types'
import { draftOf, restoreDraft } from './draftData'
import type { DraftStore } from './draftStore'
import { openDraftStore } from './draftStore'

type Input = { readonly answers: Answers; readonly outputName?: string }

export type DraftStatus = 'loading' | 'ready' | 'saving' | 'saved' | 'error' | 'delete-error'

/** Start writes on input events and mark saved only after the transaction commits. */
export function useApplicationDraft(form: PublicForm | undefined) {
  const [input, setInput] = useState<Input>({ answers: {} })
  const [status, setStatus] = useState<DraftStatus>('loading')
  const [restored, setRestored] = useState(false)
  const [changed, setChanged] = useState(false)
  const current = useRef<Input>({ answers: {} })
  const store = useRef<DraftStore>(undefined)
  const revision = useRef(0)
  const mounted = useRef(false)

  useEffect(() => {
    if (!form) return

    let active = true
    let opened: DraftStore | undefined
    mounted.current = true
    openDraftStore().then(async (connection) => {
      opened = connection
      const saved = await connection.load(form.vid)

      if (!active) {
        connection.close()

        return
      }

      store.current = connection
      const restoredInput = saved ? restoreDraft(form, saved) : { answers: {}, outputName: undefined, changed: false }
      current.current = restoredInput
      setInput(restoredInput)
      setRestored(saved !== undefined)
      setChanged(restoredInput.changed)
      setStatus(saved ? 'saved' : 'ready')
    }).catch(() => {
      // no-excuse-ok: catch - storage boundary; continue in memory and show a safe warning without logging input
      opened?.close()

      if (active) setStatus('error')
    })

    return () => {
      active = false
      mounted.current = false
      revision.current += 1
      opened?.close()
      store.current = undefined
    }
  }, [form])

  function update(next: Input) {
    current.current = next
    setInput(next)
    const connection = store.current

    if (!form || !connection) {
      setStatus('error')

      return
    }

    const request = ++revision.current
    setStatus('saving')
    connection.save(draftOf(form, next.answers, next.outputName, Date.now())).then(
      () => { if (mounted.current && request === revision.current) setStatus('saved') },
      () => { if (mounted.current && request === revision.current) setStatus('error') },
    )
  }

  async function clear(): Promise<void> {
    const connection = store.current
    const request = ++revision.current

    try {
      if (!form || !connection) {
        setStatus('delete-error')

        return
      }

      await connection.remove(form.vid)

      if (!mounted.current || request !== revision.current) return

      current.current = { answers: {} }
      setInput(current.current)
      setRestored(false)
      setChanged(false)
      setStatus(connection ? 'ready' : 'error')
    } catch {
      // no-excuse-ok: catch - deletion failure must leave current input visible and report storage failure
      if (mounted.current) setStatus('delete-error')
    }
  }

  return {
    ...input, status, restored, changed, ready: status !== 'loading', clear,
    setValues: (id: string, values: readonly string[]) => update({ ...current.current, answers: { ...current.current.answers, [id]: values } }),
    setOutputName: (outputName: string) => update({ ...current.current, outputName }),
  }
}
