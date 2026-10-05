import type { DraftStatus } from './useApplicationDraft'
import './draft.css'

const STATUS = {
  loading: '저장된 내용을 불러오고 있어요',
  ready: '입력 내용이 이 브라우저에 자동 저장돼요',
  saving: '입력 내용을 저장하고 있어요…',
  saved: '이 브라우저에 저장됐어요',
  error: '자동 저장을 사용할 수 없어요. 페이지를 나가면 입력이 사라질 수 있어요.',
  'delete-error': '저장된 내용을 지우지 못했어요. 입력은 유지됐어요. 다시 시도해 주세요.',
} satisfies Record<DraftStatus, string>

export function DraftPanel({ status, restored, changed, hasPhotos, onClear }: {
  readonly status: DraftStatus
  readonly restored: boolean
  readonly changed: boolean
  readonly hasPhotos: boolean
  readonly onClear: () => void
}) {
  return <aside className="draft-panel" aria-label="입력 내용 자동 저장">
    <p className={status === 'error' || status === 'delete-error' ? 'draft-error' : ''} role="status">{STATUS[status]}</p>
    {restored && <p>이전에 작성한 내용을 불러왔어요.{hasPhotos ? ' 사진은 다시 선택해 주세요.' : ''}</p>}
    {changed && <p>양식이 바뀌어 동일한 항목만 불러왔어요. 선택값과 빠진 항목을 확인해 주세요.</p>}
    <p>마지막 수정 후 7일 동안 보관해요. 사진과 완성 파일은 저장하지 않아요.</p>
    <button type="button" onClick={onClear}>저장된 내용 지우기</button>
  </aside>
}
