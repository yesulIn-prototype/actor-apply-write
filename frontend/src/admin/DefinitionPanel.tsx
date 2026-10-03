import { useState } from 'react'
import { message } from '../shell'
import type { Detail } from './adminApi'
import { saveDefinition } from './adminApi'
import { DefinitionGuide, STARTER } from './DefinitionGuide'

/**
 * Paste a definition or standard settings; the server validates and prepares the notice's form.
 * Keyed by the saved text, so a newly loaded definition starts a fresh editor.
 */
export function DefinitionPanel({ detail, onSaved, onError }: {
  detail: Detail
  onSaved: (detail: Detail) => void
  onError: (text: string) => void
}) {
  const [text, setText] = useState(detail.definition)
  const [saving, setSaving] = useState(false)
  const changed = text !== detail.definition

  async function save() {
    setSaving(true)
    try {
      onSaved(await saveDefinition(detail.vid, text))
    } catch (reason) { // no-excuse-ok: catch - render request errors at this UI boundary
      onError(message(reason))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="admin-section">
      <h2>{detail.standard ? '1' : '3'}. 양식 정의 JSON</h2>
      <p className="admin-help">
        지원서와 공고 본문·이미지를 확인한 AI의 JSON을 붙여넣으세요.
        지정 지원서가 있으면 원본 기준 정의를, 없으면 표준 설정을 넣어요. 표준 설정을 저장하면 지원서 파일과 배우 화면이 만들어져요.
        {detail.editingPublished && ' 공개 중인 버전을 고치면 새 버전으로 저장되고, 공개는 테스트 후 따로 해요.'}
      </p>
      <DefinitionGuide />
      <textarea
        className="admin-json"
        spellCheck={false}
        aria-label="양식 정의 JSON"
        value={text}
        placeholder="{ … }"
        onChange={(event) => setText(event.target.value)}
      />
      <div className="admin-row">
        <button type="button" className="admin-primary" disabled={saving || !changed} onClick={save}>
          {saving ? '저장 중…' : '저장하고 확인'}
        </button>
        {!text.trim() && detail.hasSource && !detail.standard && <button type="button" onClick={() => setText(STARTER)}>예시로 시작하기</button>}
        {changed && <span className="admin-muted">저장하지 않은 변경이 있어요</span>}
      </div>
      {detail.definition && (detail.problems.length > 0 ? (
        <ul className="admin-problems" aria-label="정의 문제">
          {detail.problems.map((problem) => <li key={problem}>{problem}</li>)}
        </ul>
      ) : (
        <p className="admin-ok">{detail.standard ? '지원서가 만들어졌어요.' : '정의와 원본이 맞아요.'} 아래에서 테스트 생성을 해보세요.</p>
      ))}
    </section>
  )
}
