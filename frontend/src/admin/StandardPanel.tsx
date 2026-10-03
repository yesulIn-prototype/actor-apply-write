import { useEffect, useState } from 'react'
import { message } from '../shell'
import type { CatalogItem, Detail } from './adminApi'
import { loadCatalog, saveDefinition } from './adminApi'
import { StandardExtras } from './StandardExtras'
import type { RoleMode, StandardModel } from './standardSpec'
import { EMPTY, fromSpec, toSpec } from './standardSpec'

const ROLE_MODES: readonly { readonly value: RoleMode; readonly label: string }[] = [
  { value: 'list', label: '배역 목록에서 고르기' },
  { value: 'text', label: '글로 받기' },
  { value: 'none', label: '묻지 않기 (배역이 하나뿐)' },
]

/**
 * A notice without its own form, set up on the standard form: the operator fills in what the notice asks, and
 * saving makes the notice's form file and definition on the server. Keyed by the saved settings, so a newly
 * loaded version starts fresh.
 */
export function StandardPanel({ detail, onSaved, onError }: {
  detail: Detail
  onSaved: (detail: Detail) => void
  onError: (text: string) => void
}) {
  const [model, setModel] = useState<StandardModel>(() => fromSpec(detail.definition) ?? EMPTY)
  const [catalog, setCatalog] = useState<readonly CatalogItem[]>([])
  const [saving, setSaving] = useState(false)
  const spec = toSpec(model)
  const changed = !detail.standard || spec !== toSpec(fromSpec(detail.definition) ?? EMPTY)

  useEffect(() => {
    loadCatalog().then((loaded) => setCatalog(loaded.extras)).catch((reason) => onError(message(reason)))
  }, [onError])

  function set<K extends keyof StandardModel>(key: K, value: StandardModel[K]) {
    setModel((current) => ({ ...current, [key]: value }))
  }

  async function save() {
    setSaving(true)
    try {
      onSaved(await saveDefinition(detail.vid, spec))
    } catch (reason) {
      onError(message(reason))
    } finally {
      setSaving(false)
    }
  }

  const ages = model.extras.some((extra) => extra.kind === 'catalog' && extra.key === 'age')
  const placeholders = `쓸 수 있는 값: {name} 이름, {role} 배역, {gender} 성별, {phone} 연락처${ages ? ', {age} 나이' : ''}`
  return (
    <section className="admin-section">
      <h2>1. 표준 지원서 설정</h2>
      <p className="admin-help">
        공고 본문을 보고 채워주세요. 저장하면 이 공고의 지원서 파일과 배우 화면이 만들어지고, 아래에서 바로 확인할 수 있어요.
        {detail.editingPublished && ' 공개 중인 버전을 고치면 새 버전으로 저장되고, 공개는 테스트 후 따로 해요.'}
      </p>

      <fieldset className="admin-fieldset">
        <legend>공고</legend>
        <label className="admin-field">공고 제목 (배우 화면 제목, 지원서 맨 위 줄)
          <input value={model.title} placeholder="가족 뮤지컬 〈작품명〉 배우 지원서" onChange={(event) => set('title', event.target.value)} />
        </label>
        <label className="admin-field">완성 파일 이름 규칙
          <input value={model.fileName} onChange={(event) => set('fileName', event.target.value)} />
        </label>
        <p className="admin-help">{placeholders}</p>
      </fieldset>

      <fieldset className="admin-fieldset">
        <legend>지원 정보</legend>
        <div className="admin-row" role="radiogroup" aria-label="지원 배역">
          {ROLE_MODES.map((mode) => (
            <label key={mode.value} className="admin-choice">
              <input type="radio" name="role-mode" checked={model.roleMode === mode.value} onChange={() => set('roleMode', mode.value)} />
              {mode.label}
            </label>
          ))}
        </div>
        {model.roleMode === 'list' && (
          <>
            <textarea aria-label="배역 목록" placeholder={'한 줄에 하나씩, 공고 표기 그대로\n곰 역 (멀티/남자)\n공주 역 (여자)'}
              value={model.roles} onChange={(event) => set('roles', event.target.value)} />
            <label className="admin-field admin-narrow">고를 수 있는 배역 수 (0은 제한 없음)
              <input type="number" min={0} value={model.pickRoles} onChange={(event) => set('pickRoles', Number(event.target.value))} />
            </label>
          </>
        )}
        <label className="admin-choice">
          <input type="checkbox" checked={model.askCurrent} onChange={(event) => set('askCurrent', event.target.checked)} />
          현재 출연 작품 묻기
        </label>
        <label className="admin-choice">
          <input type="checkbox" checked={model.askUnavailable} onChange={(event) => set('askUnavailable', event.target.checked)} />
          참여 불가 일정 묻기
        </label>
      </fieldset>

      <StandardExtras catalog={catalog} extras={model.extras} onChange={(extras) => set('extras', extras)} />

      <fieldset className="admin-fieldset">
        <legend>안내 문구</legend>
        <label className="admin-field">대표 사진 안내
          <input value={model.help.photoMain ?? ''} placeholder="예: 최근 6개월 이내 사진"
            onChange={(event) => set('help', { ...model.help, photoMain: event.target.value })} />
        </label>
        <label className="admin-field">출연 경력 안내
          <input value={model.help.career ?? ''} placeholder="예: 정식 공연 경력만 적어주세요"
            onChange={(event) => set('help', { ...model.help, career: event.target.value })} />
        </label>
      </fieldset>

      <fieldset className="admin-fieldset">
        <legend>제출 안내 (배우 완료 화면에 보여요)</legend>
        <label className="admin-field">받는 이메일
          <input type="email" value={model.email} onChange={(event) => set('email', event.target.value)} />
        </label>
        <label className="admin-field">메일 제목 규칙
          <input value={model.subject} placeholder="작품_{role}_{name}" onChange={(event) => set('subject', event.target.value)} />
        </label>
        <label className="admin-field admin-narrow">마감일
          <input type="date" value={model.deadline} onChange={(event) => set('deadline', event.target.value)} />
        </label>
        <label className="admin-field">함께 낼 것
          <input value={model.note} placeholder="예: 자유곡 영상 링크를 함께 보내주세요" onChange={(event) => set('note', event.target.value)} />
        </label>
      </fieldset>

      <div className="admin-row">
        <button type="button" className="admin-primary" disabled={saving || !changed || !model.title.trim()} onClick={save}>
          {saving ? '만드는 중…' : '저장하고 지원서 만들기'}
        </button>
        {changed && detail.standard && <span className="admin-muted">저장하지 않은 변경이 있어요</span>}
      </div>
      {detail.standard && (detail.problems.length > 0 ? (
        <ul className="admin-problems" aria-label="설정 문제">
          {detail.problems.map((problem) => <li key={problem}>{problem}</li>)}
        </ul>
      ) : (
        <p className="admin-ok">지원서가 만들어졌어요. 아래에서 테스트 생성을 해보세요.</p>
      ))}
    </section>
  )
}
