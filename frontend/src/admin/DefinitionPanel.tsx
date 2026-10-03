import { useState } from 'react'
import { message } from '../shell'
import type { Detail } from './adminApi'
import { saveDefinition } from './adminApi'
import { DefinitionGuide, STARTER } from './DefinitionGuide'

/**
 * The operator writes the definition as JSON; saving checks it against the uploaded form at once.
 * Keyed by the saved text, so a newly loaded definition starts a fresh editor.
 */
export function DefinitionPanel({ detail, onSaved, onError, settings = false }: {
  detail: Detail
  onSaved: (detail: Detail) => void
  onError: (text: string) => void
  /** A standard-form notice: the text is its settings, pasted from an AI or edited by hand. */
  settings?: boolean
}) {
  const [text, setText] = useState(detail.definition)
  const [saving, setSaving] = useState(false)
  const changed = text !== detail.definition

  async function save() {
    setSaving(true)
    try {
      onSaved(await saveDefinition(detail.vid, text))
    } catch (reason) {
      onError(message(reason))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="admin-section">
      <h2>{settings ? '설정 JSON 직접 고치기' : '3. 양식 정의'}</h2>
      {settings ? (
        <p className="admin-help">
          AI가 공고 본문으로 써 준 설정(<code>"base": "standard-v1"</code>)을 붙여넣거나 직접 고쳐요. 규칙은 <code>design/form-definition-guide.md</code>의 "표준 지원서 공고 설정"이에요.
        </p>
      ) : (
        <>
          <p className="admin-help">
            배우 화면의 항목(<code>items</code>)과 문서에 쓰는 규칙(<code>outputs</code>)을 적어요. 저장하면 원본과 맞는지 바로 확인해요.
            {detail.editingPublished && ' 공개 중인 버전을 고치면 새 버전으로 저장되고, 공개는 테스트 후 따로 해요.'}
          </p>
          <DefinitionGuide />
        </>
      )}
      <textarea
        className="admin-json"
        spellCheck={false}
        aria-label={settings ? '공고 설정 JSON' : '양식 정의 JSON'}
        value={text}
        placeholder="{ … }"
        onChange={(event) => setText(event.target.value)}
      />
      <div className="admin-row">
        <button type="button" className="admin-primary" disabled={saving || !changed} onClick={save}>
          {saving ? '저장 중…' : '저장하고 확인'}
        </button>
        {!text.trim() && !settings && <button type="button" onClick={() => setText(STARTER)}>예시로 시작하기</button>}
        {changed && <span className="admin-muted">저장하지 않은 변경이 있어요</span>}
      </div>
      {detail.definition && !settings && (detail.problems.length > 0 ? (
        <ul className="admin-problems" aria-label="정의 문제">
          {detail.problems.map((problem) => <li key={problem}>{problem}</li>)}
        </ul>
      ) : (
        <p className="admin-ok">정의와 원본이 맞아요. 아래에서 테스트 생성을 해보세요.</p>
      ))}
    </section>
  )
}
