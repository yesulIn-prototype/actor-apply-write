# 공고별 지원서 작성 링크

## 상태

- 확정일: 2026-10-02
- 결정자: 사용자
- 범위: 운영자의 수동 양식 정의 → vid 연결 → 테스트 생성 → 공개 → 배우 작성·다운로드

## 확정 결정

| 주제 | 결정 | 이유 |
|---|---|---|
| 양식 정의 방식 | 운영자가 JSON을 직접 쓰고 운영자 페이지(`/admin`)에서 올림·검증·테스트·공개 | 배포 없이 공고를 추가하고, 자동 추론 없이 운영자가 정한 대로만 동작한다. |
| 정의 구조 | 화면 항목(`items`)과 문서 출력(`outputs`)을 분리하고 서버가 조합 | 복합 입력, 한 값을 여러 칸에, 체크 표시를 한 구조로 표현한다. 배우 요청에는 칸 주소가 없다. |
| 보관 | 서버 볼륨(`yesulin.forms-dir`, Railway `/data/forms`)에 공고·버전별 파일 | 저장소가 공개라서 원본 지원서를 커밋하지 않는다. 재시작·재배포 후에도 남는다. |
| 권한 | `/api/admin/**`는 `yesulin.admin-token`(환경 변수 `YESULIN_ADMIN_TOKEN`) Bearer 토큰. 비어 있으면 운영자 API가 꺼진다 | 계정 없이 운영자 한 명이 쓰는 도구다. |
| 작성 링크 | `apply.yesulin.art/apply/{vid}` | 이 저장소 안에서 경로를 받는다. `yesulin.art/apply/{vid}`는 다른 저장소의 redirect가 필요하다(아래). |
| 수정·종료 | 버전 고정. 공개한 버전은 다시 쓰지 않고, 수정하면 새 버전이 된다 | 열어 둔 배우 화면은 연 버전으로 끝까지 완성되고, 새 진입은 최신 공개 버전을 본다. |

## 흐름

1. 운영자가 `/admin`에서 토큰을 넣고 공고 번호(vid)를 연다.
2. 원본 지원서(HWP/HWPX)를 올린다. HWPX는 rhwp로 HWP로 바꿔 저장한다.
3. 원본 위에 칸 주소(`표.행.칸`)가 표시된다. 운영자는 주소를 읽어 정의를 쓴다. 칸 목록에는 원문도 나온다.
4. 정의 초안은 운영자가 직접 쓰거나 에이전트(Claude Code, Codex 등)에게 대화로 맡긴다. 에이전트에게는 `design/form-definition-guide.md`, `칸 목록` 표를 복사한 것, 공고 안내를 준다. 서비스 안에는 AI도 자동 추론도 없고, 초안은 아래 검증과 테스트 생성을 똑같이 거친다.
5. 정의를 저장하면 형식·참조·원본 대조(칸 존재, `edit`의 `find` 글자)를 검사해 문제를 모두 보여준다.
6. 배우 화면과 같은 입력으로 테스트 생성을 하고, 미리보기·HWP·PDF로 확인한다.
7. 지금 원본과 정의로 테스트에 성공한 경우에만 공개된다. 공개 후 `/apply/{vid}` 링크를 공유한다.
8. 배우는 링크에서 입력하고 `지원서 만들기`를 누른다. 처음 만들 때 배우 전용 작업(UUID)이 생기고, 공용 원본을 복사해 그 작업에만 쓴다.
9. 완성 화면에서 미리보기의 칸을 눌러 수정하고, 한글·PDF 저장과 메일 전달을 한다. 인앱 브라우저에서 만든 완성 파일은 `/apply/{vid}?doc=<작업ID>`로 외부 브라우저에서 이어받는다.

## 저장 구조

```
<forms-dir>/<vid>/state.json            {"published":[1,2],"closed":false}  마지막 값이 링크가 보여주는 버전
<forms-dir>/<vid>/v<n>/source.hwp       원본(HWP 5)
<forms-dir>/<vid>/v<n>/definition.json  운영자가 쓴 정의 그대로(표준 지원서 공고는 공고 설정에서 펼친 정의)
<forms-dir>/<vid>/v<n>/spec.json        표준 지원서 공고만: 운영자가 쓴 공고 설정. 운영자 화면에는 이것이 보인다
<forms-dir>/<vid>/v<n>/version.json     원본 파일 이름, 마지막 테스트 성공 지문(원본+정의 SHA-256)
<forms-dir>/<vid>/v<n>/render/          운영자용 원본 페이지 그림(칸 위치)
```

- 배우 작업은 기존 문서 작업공간(`yesulin.workspace`, 30분 보관)에 둔다. 공용 폴더에는 배우 API가 쓰지 않는다.
- 같은 vid로 들어온 배우마다 작업 ID가 다르다. 다시 만들기는 자기 작업의 완성본만 바꾼다. 다른 공고·버전·운영자 테스트의 작업 ID를 보내면 새 작업으로 시작한다.
- 운영자 테스트 생성은 완성 횟수에 세지 않는다.

## 정의 형식

```jsonc
{
  "title": "가족 뮤지컬 「잠자는 숲속의 공주」 배우 지원서",   // 배우 화면 제목
  "fileName": "{name}_{role}_지원서",                         // 선택. 완성 파일 이름 기본값(다중 선택은 _로 연결). 배우가 화면에서 고칠 수 있다
  "items": [ /* 배우 화면, 적은 순서대로 */ ],
  "outputs": [ /* 문서 칸마다 하나 */ ],
  "submission": { "email": "…", "subject": "{name}_{role}", "deadline": "2026-10-15", "note": "…" }  // 선택. 완료 화면의 제출 안내
}
```

### 표준 지원서 공고 설정

지원서 파일이 없는 공고는 정의 대신 공고 설정을 저장한다(`"base": "standard-v1"`이 있으면 공고 설정으로 본다). 서버(`StandardForms`)가 표준 지원서 HWP의 지원 정보 표를 고쳐(추가 항목 줄 끼우기, 뺄 줄 지우기, 공고 제목 넣기, 라벨 쓰기) 그 공고의 `source.hwp`를 만들고, 기본 정의(`standard-v1.definition.json`)에 배역 목록·추가 항목·안내·제출 안내를 더한 `definition.json`을 만든다. 설정이 틀리면 저장하지 않고 409 `FORM_NOT_READY`로 고칠 곳을 모두 알린다. 원본을 다시 올리거나 일반 정의를 저장하면 공고 설정은 지워진다. 형식과 추가 항목 목록은 `form-definition-guide.md`.

### items

| type | 공통 외 설정 | 화면 | 문서에 쓰는 값 |
|---|---|---|---|
| `text` | `multiline`, `maxLength`(기본 200 / 여러 줄 2000) | 한 줄 입력 또는 여러 줄 | 입력 그대로(한 줄이면 줄바꿈을 공백으로) |
| `phone` | 없음 | 전화 키패드 | `010-1234-5678`, `02-123-4567` 형식으로 정리 |
| `single` | `options` 2개 이상 | 칩 하나 선택 | 선택지 `output`(없으면 `label`) |
| `multi` | `options`, `min`, `max` | 칩 여러 개 선택 | 선택지 표기를 출력의 `join`(기본 `, `)으로 연결 |
| `photo` | 없음 | 사진 선택 | `photo` 출력 칸에 맞춰 넣음 |
| `rows` | `columns` 1~8개(`{id, label}`), `maxRows`(1~50, 기본 10) | 줄마다 열 입력 카드, "줄 더하기" | `rows` 출력으로 줄마다 열 칸에 씀. 템플릿·파일 이름에는 못 씀 |

공통: `id`(영문 시작, 영문·숫자·_), `label`, `help`, `required`. 타입에 맞지 않는 설정, 알 수 없는 설정은 문제로 알린다.

### outputs

| 방식 | 예 | 동작 |
|---|---|---|
| `text` | `{ "cell": "0.7.1", "text": "{guardianName} / {guardianPhone}" }` | 칸 내용을 템플릿으로 바꾼다(복합 입력) |
| `append` | `{ "cell": "0.8.0", "append": "{intro}" }` | 칸의 원래 글(라벨) 아래에 쓴다 |
| `edit` | `{ "cell": "0.2.1", "edit": [{ "find": "남(  )", "replace": "남( V )", "when": "gender=m" }] }` | 칸 원문에서 `find`를 찾아 바꾼다. `when`은 `항목` 또는 `항목=선택지` |
| `photo` | `{ "cell": "0.0.4", "photo": "photo" }` | 사진 항목을 칸에 넣는다 |
| `rows` | `{ "cell": "2.1.0", "rows": "career", "columns": ["title", "role", ""], "formRows": 10, "grow": true }` | 줄 표 답을 `cell`부터 한 줄에 한 항목씩, 오른쪽으로 열마다 한 칸씩 쓴다. `""`는 그 칸을 건너뛴다(번호 칸) |

- 템플릿이 가리키는 답이 하나도 없으면 그 칸은 원본 그대로 둔다.
- `when` 없는 `edit`은 `replace`에 쓴 답이 모두 있을 때만 바꾼다(`"( {height} cm)"` 같은 빈칸 채우기).
- 한 칸에 출력은 하나다. 모든 항목은 문서나 파일 이름 어딘가에 쓰여야 한다.
- `rows`의 `formRows`는 양식에 이미 있는 줄 수다. `grow`가 켜져 있으면 그보다 많은 줄은 마지막 양식 줄을 복사해 그 아래에 끼운다(배우의 작업 사본에만, rhwp `edit insert-row`). `formRows`가 0이면 줄은 모두 새로 생기고, `cell` 바로 위 줄을 복사해 그 아래에 끼운다. 같은 표에서 끼운 줄 아래의 다른 출력 칸은 그만큼 밀려 쓰인다. `grow`가 없으면 `maxRows`가 `formRows`를 넘을 수 없다.
- 줄을 끼울 수 있는 줄: 칸이 위아래 줄과 병합되지 않은 줄. 옆으로 병합된 칸은 새 줄에서도 같은 모양으로 합친다. 표 안에 표가 있는 양식, 위아래로 병합된 줄은 테스트 생성에서 오류로 알린다.

## API

| 메서드 | 경로 | 권한 | 내용 |
|---|---|---|---|
| GET | `/api/forms/{vid}` | 공개 | 최신 공개 버전의 제목·항목·제출 안내(`submission`)·`pdfFirst`(표준 지원서 공고면 true)(칸·원문 없음). 없음 404 `FORM_NOT_FOUND`, 종료 410 `FORM_CLOSED` |
| POST | `/api/forms/{vid}/generate` | 공개 | multipart `request`(`{version, documentId, answers, fileName}`; `fileName`이 비면 템플릿) + `photo-<항목id>`. HWP 첨부와 `X-Document-Id`. 공개된 적 없는 버전 409 `FORM_CHANGED`, 답 오류 400 `INVALID_ANSWER` |
| GET | `/api/admin/forms` | 토큰 | 공고 목록 |
| GET | `/api/admin/forms/{vid}` | 토큰 | 편집 중 버전, 원본 칸 목록, 정의, 문제, 테스트 여부 |
| POST | `/api/admin/forms/{vid}/source` | 토큰 | 원본 올리기(공개된 버전이면 새 버전) |
| GET | `/api/admin/standard` | 토큰 | 표준 지원서 추가 항목 목록(`{base, extras: [{key, label, type, operatorOptions}]}`), 운영자 설정 화면의 체크 목록 |
| PUT | `/api/admin/forms/{vid}/definition` | 토큰 | 정의 저장(공개된 버전이면 새 버전). `"base"`가 있으면 표준 지원서 공고 설정으로 보고 원본·정의를 만든다. 설정 오류는 409 `FORM_NOT_READY` |
| GET | `/api/admin/forms/{vid}/layout`, `/layout/{page}` | 토큰 | 원본 페이지와 칸 위치 |
| GET | `/api/admin/forms/{vid}/form` | 토큰 | 편집 중 버전의 배우 화면 |
| POST | `/api/admin/forms/{vid}/test` | 토큰 | 테스트 생성(공개와 같은 형식) |
| POST | `/api/admin/forms/{vid}/publish`, `/close`, `/reopen` | 토큰 | 공개·종료·재공개. 조건 미달 409 `FORM_NOT_READY` |
| GET | `/api/admin/backup` | 토큰 | 모든 공고를 zip 하나로(`yesulin-forms-<날짜-시각>.zip`). 공고 폴더 구조 그대로(`<vid>/state.json`, `<vid>/v<n>/source.hwp·definition.json·version.json`) + `backup.json`(형식 번호, 만든 시각, 공고 목록). 그린 페이지(`render/`)는 빼고 필요할 때 다시 그린다 |
| POST | `/api/admin/backup` | 토큰 | multipart `backup`(zip). 이 서버에 **없는** 공고만 되살리고 있는 공고는 건너뛴다(덮어쓰지 않음). `{restored, skipped}`. 우리 백업이 아니거나, 모르는 파일·깨진 JSON이 있으면 400 `INVALID_BACKUP`이고 아무것도 들이지 않는다. 업로드 한도 20MB(공고 약 200개 분량) |

## yesulin.art와 연결 (이 저장소 밖, 아직 하지 않음)

`yesulin.art`는 `2026-yesulin` 저장소의 Next.js 앱이고 `/otr`도 그쪽 `frontend/next.config.ts`의 rewrite다. `yesulin.art/apply/{vid}`로 공유하려면 그 저장소에서 프록시가 아니라 redirect를 추가한다.

```ts
async redirects() {
  return [{ source: "/apply/:vid(\\d{1,12})", destination: "https://apply.yesulin.art/apply/:vid", permanent: false }]
}
```

rewrite(프록시)로 주소창을 유지하려면 정적 파일, `/api`, 60MB 업로드까지 함께 넘겨야 해서 권하지 않는다.

## 배포 전에 필요한 것

- Railway 서비스 변수 `YESULIN_ADMIN_TOKEN`(긴 무작위 값). 없으면 운영자 페이지가 "운영자 기능이 꺼져 있습니다"로 막힌다.
- `YESULIN_FORMS_DIR=/data/forms`는 Dockerfile에 넣었다(`/data`는 기존 볼륨).

## 이번 범위 밖

OTR 자동 수집, AI·자동 칸 추론, 새 문서 엔진, DOC/DOCX, 공연사 심사, 배우 계정·프로필 저장, 공고별 링크 미리보기 문구(`og:title`).
