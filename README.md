# 예술IN 배우 지원서 자동 완성

배우가 공고 지원서에 정보와 사진을 넣어 제출할 HWP/PDF를 만드는 서비스다. 입구는 세 개다.

| 주소 | 누가 | 하는 일 |
|---|---|---|
| `/apply/{vid}` | 배우 | 운영자가 준비해 공개한 공고별 작성 링크. 업로드 없이 항목만 채우고 완성 파일을 받는다 |
| `/admin` | 운영자 | 공고(vid)마다 원본 지원서와 양식 정의를 올리고, 테스트 생성으로 확인한 뒤 링크를 공개한다 |
| `/` | 배우 | 직접 업로드(기존 기능). 받은 `.hwp`를 올리면 칸을 자동으로 찾아 채우게 한다 |

공고별 링크는 서비스가 칸을 추론하지 않는다. 운영자가 정한 양식 정의대로만 쓴다. 설계와 정의 형식은 `design/notice-forms.md`.

## 공고별 작성 링크 운영 흐름

1. OTR에 새 공고가 올라오면 공고와 첨부 지원서를 확인한다. 지원서가 없는 공고(이메일 프로필 접수 등)는 링크를 만들지 않는다.
2. `/admin`에서 운영자 토큰을 넣고 공고 번호(otr.co.kr/audition/?vid=**22369**)를 연다.
3. **원본 지원서**(HWP/HWPX)를 올린다. 원본 위에 칸 주소(`표.행.칸`, 예 `0.2.1`)가 그려지고, `칸 목록`에 칸마다 원문·병합이 나온다.
4. **양식 정의(JSON)를 준비한다.** 직접 써도 되고, Claude Code·Codex 같은 에이전트에게 대화로 맡겨도 된다.
   - 에이전트에게 줄 것: 규칙 문서 `design/form-definition-guide.md`, 운영자 페이지 `칸 목록` 표를 복사한 것, 공고 안내(필수 항목, 파일 이름 규칙, 사진 장수).
   - 예: "design/form-definition-guide.md를 읽고 아래 칸 목록으로 22369 양식 정의 JSON을 써줘" + 칸 목록 붙여넣기. 에이전트는 JSON과 확인할 판단만 돌려준다.
   - 초안은 출발점일 뿐이다. 필수 여부, 선택지 표기, 파일 이름은 운영자가 공고 기준으로 확인한다.
   - 결과가 마음에 안 들어 AI에게 "이 부분은 이렇게 해줘"라고 다시 요청할 때는, 그 고침이 규칙 부족 때문이면 AI가 `design/form-definition-guide.md`도 같이 고치게 한다(사례집·변경 기록 포함). 다음 사람이 같은 실수를 하지 않게 하기 위해서다.
5. 정의를 붙여넣고 **저장하고 확인**을 누른다. 형식·참조·원본 대조(칸이 있는지, `edit`의 `find` 글자가 원문에 있는지) 문제가 모두 나온다.
6. **테스트 입력으로 확인**: 배우 화면 그대로 값과 사진을 넣고 테스트 생성 → 미리보기·HWP·PDF로 칸, 체크 표시, 사진 위치를 눈으로 확인한다.
7. **공개하기**: 지금 원본과 정의로 테스트에 성공했을 때만 켜진다. 공개 후 `https://apply.yesulin.art/apply/{vid}`를 오픈채팅·SNS에 공유한다.
8. 배우는 링크에서 입력하고 `지원서 만들기`를 누른다. 배우마다 작업이 따로 생기고(30분 보관), 완성 화면에서 고쳐 다시 만들면 자기 파일만 바뀐다.
9. 양식을 고치면 새 버전이 되고, 테스트 후 다시 공개한다. 이미 열어 둔 배우 화면은 연 버전으로 끝까지 완성된다. 마감되면 `공개 종료`를 누른다.

정의 예: `backend/src/test/resources/forms/sample-notice.definition.json`(합성 양식 `sample-notice.hwp`용).

## 구성

- `backend/`: Java 25, Spring Boot 4.1.1, `hwplib 1.1.11`
- `frontend/`: React 19, TypeScript, Vite
- `design/project-rules.md`: 사람과 AI가 같이 지키는 작업 규칙(루트 `CLAUDE.md`·`AGENTS.md`가 이 문서를 가리킨다)
- `design/how-it-works.md`: 운영자·에이전트·브라우저·서버가 각각 무엇을 하는지, 파일 이름과 HWP·PDF가 만들어지는 과정
- `design/notice-forms.md`: 공고별 작성 링크와 운영자 양식 정의(설계 결정, API, 저장 구조)
- `design/form-definition-guide.md`: 칸 목록으로 양식 정의 JSON을 쓰는 규칙(AI에게 주는 문서). 고침 요청이 생기면 사례집·변경 기록과 함께 고친다
- `design/input-typography.md`: 입력 글자 서식(대표 글꼴·크기) 결정
- `design/otr-form-survey-2026-10.md`: OTR 공고 189건의 지원서 형식 조사 종합(상세: `design/otr-attachment-audit-2026-10-03.md`)
- `design/roadmap-standard-form.md`: 다음 단계 결정과 우선순위(HWP 기본 + 지원서 없는 공고용 표준 지원서)

## 실행

PDF 저장에 쓰는 [rhwp](https://github.com/edwardkim/rhwp)(MIT, HWP 렌더러)를 한 번 설치한다. 고정 버전을 받아 SHA-256을 확인한 뒤 `tools/rhwp/`에 푼다(Git Bash·Linux·macOS).

```bash
bash tools/install-rhwp.sh
```

다른 위치에 두려면 `yesulin.rhwp.path`를, 폰트를 추가하려면 `yesulin.rhwp.font-paths`(쉼표 구분)를 지정한다. rhwp가 없으면 PDF 저장만 안내 메시지로 막히고 나머지는 그대로 동작한다.

터미널 두 개에서 각각 실행한다.

```powershell
cd backend
$env:GRADLE_USER_HOME = Join-Path ([System.IO.Path]::GetTempPath()) 'yesulin-actor-gradle'
.\gradlew.bat bootRun
```

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

브라우저에서 `http://127.0.0.1:5173`을 연다. Vite가 `/api` 요청을 `http://localhost:8080`으로 전달한다.
운영자 페이지(`/admin`)를 쓰려면 백엔드를 토큰과 함께 켠다: `.\gradlew.bat bootRun --args=--yesulin.admin-token=<아무 긴 값>`. 로컬 공고 데이터는 `backend/data/forms/`에 쌓인다.
이미 8080 포트를 사용 중이라 백엔드를 다른 포트로 실행한다면, 프런트엔드를 시작하기 전에 `VITE_API_TARGET`을 그 주소로 지정한다. 기존 서버가 켜진 채로 코드가 바뀌었다면 백엔드를 재시작해야 최신 문서 생성 로직이 적용된다.
백엔드는 개발 화면 출처로 5173 포트만 허용한다. Vite가 자동으로 5174 등으로 넘어갔다면 기존 5173 서버를 정리하거나 허용 출처 설정을 별도로 맞춰야 한다.

### 폰에서 쓰기

- 개발 서버는 LAN에 열려 있으므로 같은 와이파이의 폰에서 `http://<PC IP>:5173`으로 접속할 수 있다.
- **메일로 보내기(공유 시트)는 HTTPS에서만 동작한다.** 카카오톡·스레드 링크로 공유하거나 실제 폰에서 공유 시트를 쓰려면 HTTPS 터널을 연다.

```powershell
cloudflared tunnel --url http://localhost:5173
```

## 기기별 동작

| 환경 | 메일로 보내기 | 파일 저장 |
|---|---|---|
| iPhone Safari | 공유 시트에 HWP 첨부 → 메일 앱 선택 | 다운로드 |
| Android Chrome | 크롬이 HWP 공유를 막아서 저장 후 메일 작성 화면을 연다 | 다운로드 |
| 카카오톡 인앱 | 열자마자 기본 브라우저로 넘긴다 | 기본 브라우저에서 |
| 스레드·인스타 (Android) | 상단 안내의 `브라우저로 열기` | 기본 브라우저에서 |
| 스레드·인스타 (iOS) | 우상단 ··· → 외부 브라우저로 열기 안내 | Safari에서 |

- 완성 화면 미리보기는 `GET /api/documents/{id}/preview`(페이지 크기 + 칸별 누를 수 있는 영역)와 `GET /api/documents/{id}/preview/{page}`(SVG)로 받는다. 영역은 rhwp의 render tree에서 표 셀 위치를 읽어 만든다.
- 사진은 한글의 그림 개체로 넣고 줄 배치 정보를 함께 기록한다. 이 정보가 없으면 rhwp가 일부 양식(ESTC·하츄핑)에서 사진을 PDF에 그리지 않는다.
- PDF는 `GET /api/documents/{id}/completed.pdf`에서 rhwp로 한 번 만들어 재사용하고, 한글 파일을 다시 만들면 새로 만든다. 한컴 전용 글꼴은 배포할 수 없어 시스템 글꼴로 대체되므로 글자 모양만 원본과 조금 다르다.
- 완성본은 `GET /api/documents/{id}/completed`의 실제 URL로 내려받는다. 인앱 웹뷰는 `blob:` 다운로드를 처리하지 못하기 때문이다.
- 지원서 작성 화면에서 완성 파일 이름을 정할 수 있다. HWP와 PDF에 같은 이름이 적용되며, 파일명에 사용할 수 없는 문자는 서버에서 거부한다.
- 공고 파일 이름에 제출 규칙이 들어 있으면(`tf_(이름)_(성별)_(핸드폰뒷번호4자리).hwp`, `2026_이름_성별.hwp`) 입력한 값으로 채운 이름(`tf_홍길동_남_1234.hwp`)을 기본값으로 제안한다. 직접 고치면 그 이름을 쓴다.
- 이미 작성된 지원서를 다시 올리면 기존 답을 입력칸에 미리 채우고, 저장할 때 기존 답과 사진을 새 값으로 바꾼다.
- HEIC·WebP·대용량 사진은 브라우저에서 JPEG로 변환해 올린다.

## 검증

```powershell
cd backend
$env:GRADLE_USER_HOME = Join-Path ([System.IO.Path]::GetTempPath()) 'yesulin-actor-gradle'
.\gradlew.bat test
```

```powershell
cd frontend
npm.cmd test
npm.cmd run build
npm.cmd run lint
```

백엔드 회귀 테스트는 제공된 실제 지원서와 사진을 사용해 필드 추출, 텍스트 입력, 사진 삽입, 저장 후 재열기를 확인한다.
공고별 링크 테스트(`kr.yesulin.actor.form`)는 합성 양식 `sample-notice.hwp`로 정의 검증, 출력 조합(복합 입력·체크 표시·다중 선택), 운영자 공개 조건, 같은 vid의 두 배우 작업 분리, 공용 원본·정의 불변, 버전 고정과 공개 종료를 확인한다.
PowerShell에서 `npm` 실행 정책 오류가 나면 위처럼 `npm.cmd`를 사용하면 된다. 시스템의 실행 정책을 변경할 필요는 없다.

최근 받은 8개 지원서를 함께 검사하려면 아래처럼 실행한다. 검사는 각 양식에 테스트 값 하나와 테스트 사진 한 장을 넣어 HWP 재열기, PDF 렌더링, 페이지 수, 미리보기 영역을 확인한다. 실제 제출용 완성도를 보장하는 검사는 아니므로, 저장 후 모든 필수 항목·사진·별도 첨부물을 직접 확인해야 한다.

```powershell
cd backend
$env:YESULIN_FORMS_DIR = 'C:\Users\User\OneDrive\Desktop\지원서'
.\gradlew.bat test --tests '*RecentFormsAcceptanceTest'
Remove-Item Env:YESULIN_FORMS_DIR
```

### 칸 인식 정확도 채점

`frontend/eval/gold/`에 양식별 답안지(채워야 할 칸의 위치, 보여줄 이름, 입력 방식, 선택지)가 있다. 백엔드를 켠 상태에서 아래를 실행하면, 앱이 찾은 칸을 답안지와 비교해 `frontend/eval/report.json`에 점수와 틀린 곳을 남긴다.

```bash
cd frontend && npm run eval
```

- 입력 방식은 배우 화면 기준으로 채점한다: 한 줄 글자(`text`), 여러 줄(`multiline`), 선택지 칩(`choice`), 칸 안 서식(`template`), 사진(`photo`), 전화(`phone`), 이메일(`email`).
- 이름은 답안지 단어가 화면에 보이는 이름(표 제목 + 칸 이름)에 모두 들어 있으면 맞은 것으로 본다.
- 새 양식에서 틀린 게 나오면 답안지를 먼저 추가하고 규칙을 고친다. 규칙을 고친 뒤 점수가 떨어진 양식이 없는지 확인한다.
- 답안지는 규칙을 맞춘 양식들이라 점수가 높게 나온다. 처음 보는 양식의 실제 정확도는 새 양식을 답안지에 추가해 규칙을 고치기 전에 재야 알 수 있다.

## 배포

한 대의 서버에 백엔드 하나만 띄운다. 업로드한 문서는 서버 메모리와 로컬 디스크에 30분 동안만 두므로 여러 대로 나누면 안 된다.

1. `bash tools/install-rhwp.sh`로 서버 OS용 rhwp를 설치한다.
2. **한글 폰트를 설치한다.** Linux에는 기본 한글 폰트가 없어서 PDF와 미리보기 글자가 깨진다. 예: `apt install fonts-nanum fonts-noto-cjk`. 폰트를 다른 곳에 두면 `yesulin.rhwp.font-paths`에 지정한다.
3. 백엔드는 `./gradlew bootJar`로 빌드하고 `java -jar build/libs/*.jar`로 실행한다. 작업 폴더에 `data/`(완성 횟수)와 `logs/`가 생긴다.
4. 프런트는 `npm run build` 결과물 `frontend/dist/`를 nginx 같은 웹 서버로 내보내고, 같은 도메인의 `/api`를 백엔드 8080으로 넘긴다. 업로드 크기 제한은 60MB 이상으로 연다(nginx `client_max_body_size 60m`).
5. **반드시 HTTPS로 연다.** 폰의 공유 시트(메일로 보내기)는 HTTPS에서만 동작한다.

설정은 환경 변수나 `--이름=값`으로 바꾼다.

| 설정 | 기본값 | 뜻 |
|---|---|---|
| `yesulin.workspace` | 임시 폴더 | 업로드·완성 파일 임시 보관 위치 |
| `yesulin.stats-file` | `./data/completed-count.txt` | 완성 횟수 |
| `YESULIN_LOG_FILE` | `./logs/actor-api.log` | 로그 파일 (10MB씩, 14개 보관) |
| `yesulin.rate-limit.requests` / `window` | 60 / 10분 | 한 IP가 올리기·만들기를 할 수 있는 횟수 |
| `yesulin.max-documents` | 300 | 동시에 보관하는 문서 수 (넘으면 "사용자가 많아요" 안내) |
| `yesulin.cors.allowed-origins` | localhost:5173 | 프런트와 API의 도메인이 다를 때만 |
| `yesulin.forms-dir` | `./data/forms` | 공고별 원본 지원서와 양식 정의(버전별, 오래 보관) |
| `yesulin.admin-token` | (비어 있음) | 운영자 API 토큰. 비면 운영자 기능이 꺼진다 |

### Railway (운영 중)

- 주소: https://apply.yesulin.art (Cloudflare DNS `apply` CNAME → Railway, 프록시 끔). Railway 기본 주소 https://web-production-e8f7f.up.railway.app 도 그대로 동작한다. 프로젝트 `yesulin`, 서비스 `web`.
- 루트의 `Dockerfile` 하나로 프런트 빌드, 백엔드 jar, rhwp, 한글 폰트를 묶어 같은 주소에서 서빙한다.
- 볼륨 `web-volume`을 `/data`에 연결했다. 완성 횟수(`/data/completed-count.txt`), 로그(`/data/logs`), 공고별 양식(`/data/forms`)은 재배포해도 남는다.
- 운영자 페이지를 쓰려면 서비스 변수 `YESULIN_ADMIN_TOKEN`에 긴 무작위 값을 넣는다. 비어 있으면 `/api/admin/**`가 꺼진다.
- 서비스 변수: `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES=.*`, `SERVER_TOMCAT_REMOTEIP_REMOTE_IP_HEADER=x-real-ip`. Railway 중계 서버가 실제 사용자 IP를 `X-Real-IP`에 덮어써서 넘겨주므로 이 값을 믿는다. 이 설정이 없으면 모든 사용자가 중계 서버 IP로 묶여 횟수 제한을 함께 쓴다.
- 배포: 서비스 `web`이 GitHub `yesulIn-prototype/actor-apply-write`의 `main`에 연결돼 있고 Wait for CI가 켜져 있다. `main`에 push하면 GitHub Actions CI(백엔드·프런트 테스트, 빌드)가 돌고, 통과한 커밋만 Railway가 자동으로 빌드·배포한다. CI가 실패하면 배포되지 않는다. 로그 보기: `railway logs`.

### 로그

- `kr.yesulin.actor.access`: API 호출마다 한 줄씩 남긴다(메서드, 경로, 상태, 걸린 시간, IP).
- `DocumentService`: 분석·생성·PDF 결과를 남긴다(문서 ID, 크기, 칸 수, 걸린 시간). 칸을 하나도 못 찾은 양식은 `no fields found`로 남으므로 새 양식에서 파싱이 실패한 경우를 찾을 수 있다. 공고별 링크는 `job started`·`generated job`(작업 ID, `form:<vid>:v<버전>`, 칸 수)으로 남는다.
- `AdminTokenFilter`: 운영자 토큰이 틀린 요청을 IP와 경로로 남긴다.
- 오류: 잘못된 업로드는 INFO, 문서 처리 실패는 WARN(원인 포함), 예상하지 못한 오류는 ERROR(스택 포함)로 남긴다.
- **개인정보를 남기지 않는다.** 파일 이름, 칸 이름, 입력값, 사진은 로그에 쓰지 않는다. 프록시 뒤에서는 `X-Forwarded-For`의 실제 IP를 쓴다.

### 방문 분석 (GA4)

- 속성 `G-DDJ8ZGPF8Q`(`frontend/src/analytics.ts`). `apply.yesulin.art`에서만 보낸다. 로컬, 터널, Railway 기본 주소는 보내지 않는다.
- 화면마다 가상 페이지로 보낸다: `/` 올리기, `/fill` 작성, `/done` 완성, `/resume` 인앱에서 완성하고 브라우저에서 이어 연 경우, `/apply/fill`·`/apply/done` 공고별 링크의 작성·완성. 운영자 페이지는 보내지 않는다.
- 이벤트: `save_hwp`, `save_pdf`, `send_mail`(`method`: share·download), `send_mail_cancel`, `analyze_error`·`generate_error`·`pdf_error`(`reason`: 화면에 띄운 안내 문구), `open_external_browser`(`from`: start·done).
- 유입: 링크에 UTM이 있으면 그대로 쓴다. 없으면 인앱 브라우저 이름을 `utm_source`로 붙인다(`kakaotalk`, `threads`, `instagram`…, `utm_medium=social`). 카카오톡은 리퍼러를 보내지 않고, 기본 브라우저로 넘어가면 앱 정보도 사라지기 때문이다. 넘기기 직전의 인앱 방문은 세지 않고 넘어간 쪽에서 센다.
- **보내는 주소는 화면 경로와 UTM뿐이다.** `?doc=<id>`만 있으면 완성본을 받을 수 있으므로 절대 보내지 않는다. 입력값, 파일 이름도 보내지 않는다.
- GA 설정에서 **향상된 측정은 끈다.** 켜 두면 GA가 실제 주소와 다운로드 링크(`/api/documents/{id}/completed.pdf`)를 직접 모은다.

## 파일 보존

- 업로드는 OS 임시 디렉터리 아래 `yesulin-actor` 작업공간에 저장된다.
- 메모리의 문서 참조는 기본 30분 뒤 만료된다.
- 만료 정리는 5분 간격으로 실행되며, 삭제 실패 항목은 다음 주기에 다시 시도한다.
- 서버 시작 시 보관 시간을 넘긴 고아 작업 디렉터리도 정리한다.
- 공고별 원본 지원서와 양식 정의는 `yesulin.forms-dir`에 계속 둔다. 배우의 입력·사진·완성본은 위의 30분 작업공간에만 있다.
- 데이터베이스나 사용자 계정은 사용하지 않는다.
- 첫 화면의 완성 횟수는 `backend/data/completed-count.txt`에 숫자 하나로만 저장한다(`yesulin.stats-file`). 같은 문서를 다시 완성해도 한 번만 센다.
