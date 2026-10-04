# 예술IN 배우 지원서 자동 완성

배우가 공고 지원서에 정보와 사진을 넣어 제출할 HWP/PDF를 만드는 서비스다. 작성은 공고별 링크에서 시작한다.

| 주소 | 누가 | 하는 일 |
|---|---|---|
| `/apply/{vid}` | 배우 | 운영자가 준비해 공개한 공고별 작성 링크. 업로드 없이 항목만 채우고 완성 파일을 받는다 |
| `/admin` | 운영자 | 공고(vid)마다 원본 지원서와 양식 정의를 올리고, 테스트 생성으로 확인한 뒤 링크를 공개한다 |

공고별 링크는 서비스가 칸을 추론하지 않는다. 운영자가 정한 양식 정의대로만 쓴다. 설계와 정의 형식은 `design/notice-forms.md`.

## 공고별 작성 링크 운영 흐름

1. OTR에 새 공고가 올라오면 본문·이미지·첨부와 접수 방식을 확인한다. 지정 HWP/HWPX가 있으면 원본을 쓰고, 지정 파일 없이 자유양식·프로필 파일을 받는 공고는 표준 지원서로 준비한다. 온라인 폼 전용이나 다른 지정 형식은 별도로 판단한다.
2. `/admin`에서 운영자 토큰을 넣고 공고 번호(otr.co.kr/audition/?vid=**22369**)를 연다.
3. **지정 지원서가 있으면 원본**(HWP/HWPX)을 올린다. 원본 위에 칸 주소(`표.행.칸`, 예 `0.2.1`)가 그려지고, `칸 목록`에 칸마다 원문·병합이 나온다. **지정 지원서가 없으면 업로드를 건너뛴다.** 새 공고는 선택 화면 없이 원본 업로드와 JSON 입력란을 바로 보여준다.
4. **양식 정의(JSON)를 준비한다.** 직접 써도 되고, Claude Code·Codex 같은 에이전트에게 대화로 맡겨도 된다.
   - 에이전트에게 줄 것: 규칙 문서 `design/form-definition-guide.md`, vid, **OTR 본문과 공고 이미지 전체**, 지정 지원서가 있으면 파일과 운영자 페이지의 `칸 목록`. 이미지 읽기/OCR은 각 팀원의 외부 에이전트가 한다.
   - 지정 지원서가 있으면 정의 JSON, 없으면 `standard-v1` 설정 JSON 하나를 받는다. 둘 다 **양식 정의 JSON**에 붙여넣는다. 팀원이 복사해 쓰는 요청문은 가이드에 있다.
   - 에이전트는 추가 질문·사진 조건·파일 이름·제출 형식·메일 제목·마감을 대조해 요구사항 반영표와 확인 목록도 준다. 파일 이름과 메일 제목은 별도 규칙이다. 현재 JSON으로 강제할 수 없는 형식·장수·시각 조건은 안내와 미지원 사항으로 구분한다.
   - 초안은 출발점이다. 공고와 원본의 충돌, OCR 불확실성, 필수 여부·선택지·파일 이름은 운영자가 확인한다. 공고 조건 전체를 자동 검증하거나 접수 완료를 보장하는 흐름은 아니다.
   - 결과가 마음에 안 들어 AI에게 "이 부분은 이렇게 해줘"라고 다시 요청할 때는, 그 고침이 규칙 부족 때문이면 AI가 `design/form-definition-guide.md`도 같이 고치게 한다(사례집·변경 기록 포함). 다음 사람이 같은 실수를 하지 않게 하기 위해서다.
5. **저장하고 확인**을 누른다. 일반 정의는 형식·참조·원본 대조(칸이 있는지, `edit`의 `find` 글자가 원문에 있는지) 문제를 확인한다. 표준 설정은 서버가 공고용 HWP와 정의를 만들어 저장한다.
6. **테스트 입력으로 확인**: 배우 화면 그대로 값과 사진을 넣고 테스트 생성 → 미리보기·HWP·PDF로 칸, 체크 표시, 사진 위치를 눈으로 확인한다.
7. **공개하기**: 지금 원본과 정의로 테스트에 성공했을 때만 켜진다. 공개 후 `https://apply.yesulin.art/apply/{vid}`를 오픈채팅·SNS에 공유한다.
8. 배우는 링크에서 입력하고 `지원서 만들기`를 누른다. 배우마다 작업이 따로 생기고(30분 보관), 완성 화면에서 고쳐 다시 만들면 자기 파일만 바뀐다.
9. 양식을 고치면 새 버전이 되고, 테스트 후 다시 공개한다. 이미 열어 둔 배우 화면은 연 버전으로 끝까지 완성된다. 마감되면 `공개 종료`를 누른다.

정의 예: `backend/src/test/resources/forms/sample-notice.definition.json`(합성 양식 `sample-notice.hwp`용).

## 구성

- `backend/`: Java 25, Spring Boot 4.1.1, `hwplib 1.1.11`
- `frontend/`: React 19, TypeScript, Vite
- `tools/standard-form/`: 예술in 표준 지원서(`backend/src/main/resources/standard/standard-v1.hwp`)를 rhwp로 다시 만드는 스크립트(`bash tools/standard-form/build.sh`)
- `design/project-rules.md`: 사람과 AI가 같이 지키는 작업 규칙(루트 `CLAUDE.md`·`AGENTS.md`가 이 문서를 가리킨다)
- `design/agent-skills.md`: Codex·Claude Code 공통 외부 스킬 적용 안내와 출처(`design/skills/`에 원문·부속 파일 보관)
- `design/how-it-works.md`: 운영자·에이전트·브라우저·서버가 각각 무엇을 하는지, 파일 이름과 HWP·PDF가 만들어지는 과정
- `design/notice-forms.md`: 공고별 작성 링크와 운영자 양식 정의(설계 결정, API, 저장 구조)
- `design/form-definition-guide.md`: 지원서·칸 목록과 OTR 이미지·본문으로 정의/표준 설정 JSON을 쓰는 에이전트 규칙, 팀원 요청문, 반영표와 지원 한계. 고침 요청이 생기면 사례집·변경 기록과 함께 고친다
- `design/input-typography.md`: 입력 글자 서식(대표 글꼴·크기) 결정
- `design/otr-form-survey-2026-10.md`: OTR 공고 189건의 지원서 형식 조사 종합(상세: `design/otr-attachment-audit-2026-10-03.md`)
- `design/standard-items.md`: P1 결과. HWP 지원서 44종에서 뽑은 항목별 등장 비율, 경력·사진 구조
- `design/standard-extras.md`: 지원서 없는 공고 16건의 본문 요구사항(배역 고르기, 메일 제목 규칙, 영상 링크 등)과 추가 항목·제출 안내 제안
- `design/standard-form.md`: P2 설계. 표준 지원서 v1, 추가 항목·배우 자유·줄 표(`rows`) 결정, 구현 순서와 진행
- `design/roadmap-standard-form.md`: 다음 단계 결정과 우선순위(HWP 기본 + 지원서 없는 공고용 표준 지원서)
- [도메인 설계표](design/domain-design.md): 전체 업무·모델·상태 전이·저장 경계
- [사용자 정책](design/user-policy.md): 배우의 작성·수정·보관·파일 전달과 제한
- [운영자 정책](design/operator-policy.md): 공고 준비·공개·종료·백업·검증 절차
- [2026-10-03 서비스 점검](design/service-review-2026-10-03.md): 배포·실사이트·테스트 근거와 남은 작업
- [공고별 MVP 평가표](design/mvp-evaluation.md): 요구사항·모바일 입력·결과 문서·수정·제출 확인과 회차별 성능 지표
- [HWP 웹 직접 편집 시험](design/hwp-web-editor-poc.md): 지원동기 이동·안내문 삭제의 실제 시험 결과와 도입 선택지
- [번호로 완성 지원서 수정](design/numbered-document-editing.md): 구현 범위·재생성 확인 정책·로컬 검증 결과

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

브라우저에서 `http://127.0.0.1:5173/admin`을 연다. Vite가 `/api` 요청을 `http://localhost:8080`으로 전달한다. 배우는 공개된 `/apply/{vid}` 링크로 들어온다. 루트 주소는 공고 링크로 접속하라는 안내만 표시한다.
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
- 같은 브라우저에서 만든 완성본은 **지원서 직접 수정**으로 번호를 선택해 글 수정·비우기·옮기기를 한다. 수정 결과로 HWP·미리보기·PDF를 갱신한다. 입력 화면에서 다시 만들면 직접 수정이 사라지므로 먼저 확인한다. 지원 범위와 배포 전 확인은 [번호 편집 문서](design/numbered-document-editing.md)를 따른다.
- 사진은 한글의 그림 개체로 넣고 줄 배치 정보를 함께 기록한다. 이 정보가 없으면 rhwp가 일부 양식(ESTC·하츄핑)에서 사진을 PDF에 그리지 않는다.
- PDF는 `GET /api/documents/{id}/completed.pdf`에서 rhwp로 한 번 만들어 재사용하고, 한글 파일을 다시 만들면 새로 만든다. 한컴 전용 글꼴은 배포할 수 없어 시스템 글꼴로 대체되므로 글자 모양만 원본과 조금 다르다.
- 완성본은 `GET /api/documents/{id}/completed`의 실제 URL로 내려받는다. 인앱 웹뷰는 `blob:` 다운로드를 처리하지 못하기 때문이다.
- 지원서 작성 화면에서 완성 파일 이름을 정할 수 있다. HWP와 PDF에 같은 이름이 적용되며, 파일명에 사용할 수 없는 문자는 서버에서 거부한다.
- 운영자가 정의한 파일 이름 템플릿을 입력값으로 채워 기본 이름을 제안한다. 배우가 고치면 그 이름을 쓴다.
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

백엔드 회귀 테스트는 정의된 칸의 텍스트 입력, 사진 삽입, 줄 확장과 저장 후 재열기를 확인한다.
공고별 링크 테스트(`kr.yesulin.actor.form`)는 합성 양식 `sample-notice.hwp`로 정의 검증, 출력 조합(복합 입력·체크 표시·다중 선택), 운영자 공개 조건, 같은 vid의 두 배우 작업 분리, 공용 원본·정의 불변, 버전 고정과 공개 종료를 확인한다.
PowerShell에서 `npm` 실행 정책 오류가 나면 위처럼 `npm.cmd`를 사용하면 된다. 시스템의 실행 정책을 변경할 필요는 없다.

공고마다 운영자 테스트로 미리보기·HWP·PDF를 확인하고, [MVP 평가표](design/mvp-evaluation.md)로 요구사항 반영·모바일 입력·문서 품질·수정·제출을 기록한다. 실제 한글 앱과 휴대폰 확인은 자동 테스트와 별도로 수행한다.

## 배포

한 대의 서버에 백엔드 하나만 띄운다. 배우 작업은 서버 메모리와 로컬 디스크에 30분 동안만 두므로 여러 대로 나누면 안 된다.

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
- **공고 양식 백업**: 볼륨 한 곳에만 있으므로 운영자 페이지(`/admin`) 아래 "백업 내려받기"로 zip을 받아 따로 보관한다(공고를 새로 공개하거나 고친 뒤). 볼륨을 잃거나 서버를 옮기면 새 서버의 같은 곳에서 "백업 파일로 복원"으로 올린다. 이미 있는 공고는 덮어쓰지 않는다. 형식은 `design/notice-forms.md`의 API 표. 실제 운영 인프라로 옮기면 같은 폴더 구조로 S3에 보관할 예정이다.
- 운영자 페이지를 쓰려면 서비스 변수 `YESULIN_ADMIN_TOKEN`에 긴 무작위 값을 넣는다. 비어 있으면 `/api/admin/**`가 꺼진다.
- 서비스 변수: `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES=.*`, `SERVER_TOMCAT_REMOTEIP_REMOTE_IP_HEADER=x-real-ip`. Railway 중계 서버가 실제 사용자 IP를 `X-Real-IP`에 덮어써서 넘겨주므로 이 값을 믿는다. 이 설정이 없으면 모든 사용자가 중계 서버 IP로 묶여 횟수 제한을 함께 쓴다.
- 배포: 서비스 `web`이 GitHub `yesulIn-prototype/actor-apply-write`의 `main`에 연결돼 있고 Wait for CI가 켜져 있다. `main`에 push하면 GitHub Actions CI(백엔드·프런트 테스트, 빌드)가 돌고, 통과한 커밋만 Railway가 자동으로 빌드·배포한다. CI가 실패하면 배포되지 않는다. 로그 보기: `railway logs`.

### 로그

- `kr.yesulin.actor.access`: API 호출마다 한 줄씩 남긴다(메서드, 경로, 상태, 걸린 시간, IP). 경로 속 작업 ID는 `<job>`으로 가린다(`/api/documents/<job>/completed`).
- `DocumentService`: 분석·생성·PDF 결과를 남긴다(크기, 칸 수, 걸린 시간). 칸을 하나도 못 찾은 양식은 `no fields found`로 남으므로 새 양식에서 파싱이 실패한 경우를 찾을 수 있다. 공고별 링크는 `job started`·`generated job`(`form:<vid>:v<버전>`, 칸 수, 끼운 줄 수)으로 남는다.
- **작업 ID는 남기지 않는다.** 작업 ID만 있으면 30분 동안 완성본을 받을 수 있기 때문이다. 메시지는 코드에서(`JobIds`), 예외 스택은 `logging.exception-conversion-word` 설정에서 `<job>`으로 가린다.
- `AdminTokenFilter`: 운영자 토큰이 틀린 요청을 IP와 경로로 남긴다.
- 오류: 잘못된 업로드는 INFO, 문서 처리 실패는 WARN(원인 포함), 예상하지 못한 오류는 ERROR(스택 포함)로 남긴다. rhwp가 60초 안에 끝나지 않으면 프로세스를 끝내고 `문서 변환 시간이 초과되었습니다.`로 실패한다.
- **개인정보를 남기지 않는다.** 파일 이름, 칸 이름, 입력값, 사진은 로그에 쓰지 않는다. 프록시 뒤에서는 `X-Forwarded-For`의 실제 IP를 쓴다.

### 방문 분석 (GA4)

- 속성 `G-DDJ8ZGPF8Q`(`frontend/src/analytics.ts`). `apply.yesulin.art`에서만 보낸다. 로컬, 터널, Railway 기본 주소는 보내지 않는다.
- 화면마다 가상 페이지로 보낸다: `/apply/fill`·`/apply/done` 공고별 링크의 작성·완성, `/apply/resume` 외부 브라우저에서 완성 파일을 이어 연 경우. 운영자 페이지는 보내지 않는다.
- 이벤트: `save_hwp`, `save_pdf`, `send_mail`(`method`: share·download), `send_mail_cancel`, `generate_error`·`pdf_error`(`reason`: 화면에 띄운 안내 문구), `open_external_browser`(`from`: start·done).
- 유입: 링크에 UTM이 있으면 그대로 쓴다. 없으면 인앱 브라우저 이름을 `utm_source`로 붙인다(`kakaotalk`, `threads`, `instagram`…, `utm_medium=social`). 카카오톡은 리퍼러를 보내지 않고, 기본 브라우저로 넘어가면 앱 정보도 사라지기 때문이다. 넘기기 직전의 인앱 방문은 세지 않고 넘어간 쪽에서 센다.
- **보내는 주소는 화면 경로와 UTM뿐이다.** `?doc=<id>`만 있으면 완성본을 받을 수 있으므로 절대 보내지 않는다. 입력값, 파일 이름도 보내지 않는다.
- GA 설정에서 **향상된 측정은 끈다.** 켜 두면 GA가 실제 주소와 다운로드 링크(`/api/documents/{id}/completed.pdf`)를 직접 모은다.

## 파일 보존

- 배우별 원본 사본·사진·완성본은 OS 임시 디렉터리 아래 `yesulin-actor` 작업공간에 저장된다.
- 메모리의 문서 참조는 기본 30분 뒤 만료된다.
- 만료 정리는 5분 간격으로 실행되며, 삭제 실패 항목은 다음 주기에 다시 시도한다.
- 서버 시작 시 보관 시간을 넘긴 고아 작업 디렉터리도 정리한다.
- 공고별 원본 지원서와 양식 정의는 `yesulin.forms-dir`에 계속 둔다. 배우의 입력·사진·완성본은 위의 30분 작업공간에만 있다.
- 데이터베이스나 사용자 계정은 사용하지 않는다.
- 완성 횟수는 `backend/data/completed-count.txt`에 숫자 하나로만 저장한다(`yesulin.stats-file`). 같은 문서를 다시 완성해도 한 번만 센다. 공개 화면에서 조회·표시하는 API는 제공하지 않는다.
