/** A starting point with one item of every type; the cells are placeholders to replace. */
export const STARTER = `{
  "title": "공고 제목 지원서",
  "fileName": "{name}_지원서",
  "items": [
    { "id": "name", "label": "이름", "type": "text", "required": true },
    { "id": "phone", "label": "연락처", "type": "phone", "required": true },
    { "id": "intro", "label": "자기소개", "type": "text", "multiline": true, "maxLength": 800 },
    { "id": "gender", "label": "성별", "type": "single",
      "options": [ { "id": "m", "label": "남" }, { "id": "f", "label": "여" } ] },
    { "id": "role", "label": "지원 배역", "type": "multi", "max": 2,
      "options": [ { "id": "a", "label": "배역 A" }, { "id": "b", "label": "배역 B" } ] },
    { "id": "photo", "label": "프로필 사진", "type": "photo", "required": true }
  ],
  "outputs": [
    { "cell": "0.0.1", "text": "{name} / {phone}" },
    { "cell": "0.1.1", "append": "{intro}" },
    { "cell": "0.2.1", "edit": [
      { "find": "남(  )", "replace": "남( V )", "when": "gender=m" },
      { "find": "여(  )", "replace": "여( V )", "when": "gender=f" } ] },
    { "cell": "0.3.1", "text": "{role}", "join": ", " },
    { "cell": "0.0.4", "photo": "photo" }
  ]
}`

export function DefinitionGuide() {
  return (
    <details className="admin-guide">
      <summary>쓰는 법</summary>
      <dl>
        <dt>items (배우 화면, 적은 순서대로 보임)</dt>
        <dd>
          공통: <code>id</code>(영문) · <code>label</code> · <code>help</code> · <code>required</code> · <code>type</code><br />
          <code>text</code>: <code>multiline</code>, <code>maxLength</code> · <code>phone</code>: 010-0000-0000 형식으로 정리됨 ·
          {' '}<code>single</code>/<code>multi</code>: <code>options</code>[{'{'}id, label, output{'}'}], multi는 <code>min</code>·<code>max</code> ·
          {' '}<code>photo</code> ·
          {' '}<code>rows</code>(경력 같은 줄 표): <code>columns</code>[{'{'}id, label{'}'}], <code>maxRows</code>
        </dd>
        <dt>outputs (문서 칸마다 하나, <code>cell</code>은 "표.행.칸")</dt>
        <dd>
          <code>text</code>: 칸 내용을 템플릿으로 바꿈. <code>{'{id}'}</code>로 답을 넣고, 여러 답을 한 칸에 합칠 수 있음 ·
          {' '}<code>append</code>: 칸의 원래 글(라벨) 아래에 씀 ·
          {' '}<code>edit</code>: 원문 일부만 바꿈(<code>find</code>→<code>replace</code>, <code>when</code>: "id" 또는 "id=선택지") ·
          {' '}<code>photo</code>: 사진 항목 id ·
          {' '}<code>rows</code>: 줄 표 항목 id, <code>cell</code>은 첫 줄 첫 칸, <code>columns</code>는 칸마다 열 id(<code>""</code>는 건너뜀),
          {' '}<code>formRows</code>(양식 줄 수), <code>grow</code>(넘치면 줄 추가)
        </dd>
        <dt>선택 결과를 쓰는 방법</dt>
        <dd>
          선택지 이름으로: <code>{'"text": "{role}"'}</code> (option의 <code>output</code>이 있으면 그 글자, 여러 개는 <code>join</code>으로 연결) ·
          {' '}원문에 표시: <code>edit</code>으로 <code>"□곰역"→"■곰역"</code>, <code>"남(  )"→"남( V )"</code>
        </dd>
        <dt>그 밖</dt>
        <dd>
          <code>fileName</code>: 완성 파일 이름 템플릿(다중 선택은 _로 연결) · 답이 하나도 없는 칸은 원본 그대로 둬요 · 모든 항목은 문서나 파일 이름 어딘가에 쓰여야 해요
        </dd>
      </dl>
    </details>
  )
}
