import { useEffect, useState } from 'react'
import type { Completed, Preview } from '../api'
import { fetchPreview, previewPageUrl } from '../api'
import { FormFields } from '../form/FormFields'
import type { PublicForm } from '../form/types'
import { useAnswers } from '../form/useAnswers'
import { message } from '../shell'
import type { Detail } from './adminApi'
import { loadEditingForm, testBuild } from './adminApi'

/**
 * The applicant's screen for the version being edited, filled with test answers. A successful build of
 * exactly this source and definition is what publishing requires; the file is checked here by eye.
 */
export function TestPanel({ vid, detail, revision, onTested }: {
  vid: string
  detail: Detail
  revision: number
  onTested: () => void
}) {
  const [form, setForm] = useState<PublicForm>()
  const [error, setError] = useState('')
  const [building, setBuilding] = useState(false)
  const [result, setResult] = useState<{ completed: Completed; preview?: Preview }>()
  const answers = useAnswers(setError)

  useEffect(() => {
    let active = true
    loadEditingForm(vid)
      .then((loaded) => { if (active) setForm(loaded) })
      .catch((reason) => { if (active) setError(message(reason)) })
    return () => { active = false }
  }, [vid, revision])

  async function build() {
    if (!form) return
    setBuilding(true)
    setError('')
    try {
      const completed = await testBuild(vid, form, answers.answers, answers.photos)
      setResult({ completed })
      onTested()
      setResult({ completed, preview: await fetchPreview(completed.documentId) })
    } catch (reason) {
      setError(message(reason))
    } finally {
      setBuilding(false)
    }
  }

  return (
    <section className="admin-section">
      <h2>{detail.standard ? '2' : '4'}. 테스트 입력으로 확인</h2>
      <p className="admin-help">
        배우가 볼 화면 그대로예요. 값을 넣고 만들어 본 뒤 칸·체크·사진 위치를 눈으로 확인하세요.
        {detail.tested ? ' 지금 내용으로 테스트를 마쳤어요.' : ' 정의나 원본을 바꾸면 다시 테스트해야 공개할 수 있어요.'}
      </p>
      {error && <p className="admin-error" role="alert">{error}</p>}
      {form && (
        <div className="admin-test-form">
          <h3>{form.title}</h3>
          <FormFields form={form} answers={answers.answers} photos={answers.photos} onValues={answers.setValues} onPhoto={answers.pickPhoto} />
          <div className="admin-row">
            <button type="button" className="admin-primary" disabled={building} onClick={build}>
              {building ? '만드는 중…' : '테스트 생성'}
            </button>
          </div>
        </div>
      )}
      {result && (
        <div className="admin-result">
          <p>
            {result.completed.file.name} ·{' '}
            <a href={result.completed.downloadUrl} download={result.completed.file.name}>HWP 받기</a> ·{' '}
            <a href={result.completed.pdfUrl} download>PDF 받기</a>
          </p>
          {!result.preview && <p className="admin-help">미리보기를 그리는 중…</p>}
          <div className="admin-pages">
            {result.preview?.pages.map((page) => (
              <div key={page.number} className="admin-page" style={{ aspectRatio: `${page.width} / ${page.height}` }}>
                <img src={previewPageUrl(result.completed.documentId, page.number, revision)} alt={`테스트 결과 ${page.number}쪽`} />
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}
