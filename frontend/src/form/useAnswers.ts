import { useRef, useState } from 'react'
import { preparePhoto } from '../photo'
import type { Answers, Photos } from './types'

/** The answers to a notice form, kept in the browser only until the file is built. */
export function useAnswers(onError: (text: string) => void) {
  const [answers, setAnswers] = useState<Answers>({})
  const [photos, setPhotos] = useState<Photos>({})
  const snapshot = useRef<{ answers: Answers; photos: Photos }>(undefined)

  function setValues(id: string, values: readonly string[]) {
    setAnswers((current) => ({ ...current, [id]: values }))
  }

  async function pickPhoto(id: string, file?: File) {
    if (!file) {
      setPhotos((current) => ({ ...current, [id]: undefined }))

      return
    }

    try {
      const prepared = await preparePhoto(file)
      setPhotos((current) => ({ ...current, [id]: prepared }))
    } catch (reason) {
      onError(reason instanceof Error ? reason.message : '사진을 불러오지 못했어요')
    }
  }

  return {
    answers,
    photos,
    setValues,
    pickPhoto,
    /** Before an edit sheet opens, so closing it can undo what it changed. */
    remember: () => { snapshot.current = { answers, photos } },
    restore: () => {
      if (!snapshot.current) return
      setAnswers(snapshot.current.answers)
      setPhotos(snapshot.current.photos)
    },
  }
}
