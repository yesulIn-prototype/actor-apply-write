# 예술in 지원서 서비스 — 도메인 설계표

기준: 2026-10-03. 현재 구현을 정리한 문서다. 새로운 구조나 정책을 확정하는 문서가 아니다.

- [사용자 정책](user-policy.md)
- [운영자 정책](operator-policy.md)
- [점검 결과와 남은 작업](service-review-2026-10-03.md)
- API 상세: [notice-forms.md](notice-forms.md), 실행 구조: [how-it-works.md](how-it-works.md)

## 1. 서비스의 목적과 경계

운영자가 OTR 공고와 함께 공고별 작성 링크를 전달한다. 배우는 모바일에서도 정보·사진을 입력해 지원서 파일을 만들고 자신의 메일 앱으로 제출한다. 서비스가 보장하는 작업은 파일 작성·미리보기·다운로드·전달 보조이며, 공연사의 접수나 실제 이메일 발송 성공은 확인하지 않는다.

서비스의 기능 입구는 공고별 작성 링크와 운영자 화면이다. 루트 주소와 잘못된 경로에는 공고 링크로 접속하라는 안내만 표시한다.

| 입구 | 사용자 | 도메인 흐름 |
|---|---|---|
| /apply/{vid} | 배우 | 공개된 공고 양식 → 정의된 항목 입력 → 개인 작업 생성 → 수정 → HWP/PDF·제출 안내 |
| /admin | 운영자 | 지정 원본 업로드 또는 표준 양식 설정 → 검증 → 테스트 → 공개·종료·재개 → 백업 |

공고별 링크는 운영자가 정의한 항목과 출력 규칙을 따른다. 서비스 내부 AI 호출이나 자동 칸 추론은 없다. 외부 AI와의 대화는 운영자가 정의·설정 초안을 작성하는 보조 수단이다.

## 2. 도메인 설계표

아래 묶음은 설명을 위한 업무 구분이다. 별도 서비스나 데이터베이스 분리를 뜻하지 않는다.

| 도메인 | 식별·주요 데이터 | 책임·핵심 규칙 | 저장·수명 | 구현 근거 |
|---|---|---|---|---|
| 공고 | Vid, 숫자 문자열 1~12자리 | OTR 번호로 작성 링크 식별. OTR 존재·원문·마감 변경은 자동 확인하지 않음 | 공고 폴더 지속 보관 | Vid, FormStore |
| 공고 공개 상태 | published 버전 목록, closed | 최신 공개 버전 제공. 초안·공개·종료 구분. 마감일과 종료 상태는 별개 | state.json | FormApplyService, FormAdminService |
| 양식 버전 | vid + version | 공개한 버전 불변. 수정은 새 초안. 기존 배우는 열었던 공개 버전으로 생성 | vN 폴더 지속 보관 | FormStore.draft |
| 지정 원본 | source.hwp, originalName | HWP/HWPX 입력, HWPX는 HWP 5로 변환. DOCX·DOC·XLSX 미지원 | source.hwp, version.json | UploadValidator, DocumentService |
| 양식 정의 | title, fileName, items, outputs, submission | 화면 입력과 문서 쓰기 분리. 참조·선택지·원본 칸 검증 | definition.json | FormDefinitionParser, SourceCheck |
| 입력 항목 | item.id, type, label, help, required | text·phone·single·multi·photo·rows. 필수·길이·선택 개수 검사 | 공용 정의 안에 저장 | FormItem, ItemParser, FormAnswers |
| 출력 규칙 | 표.행.칸 주소, 출력 방식 | text·append·edit·photo·rows. 한 입력을 여러 칸에 쓰거나 여러 입력을 한 칸에 조합 | 공용 정의 안에 저장 | FormOutput, OutputParser, FormComposer |
| 표준 양식 자산 | standard-v1 | 합성 HWP와 기본 정의·추가 항목 목록. 재생성 스크립트 제공 | 저장소·배포 파일 | resources/standard, tools/standard-form |
| 표준 공고 설정 | base, title, roles, pickRoles, drop, extras, help, submission | 공고 원본과 전체 정의를 생성. 이후 일반 양식과 같은 검증·테스트·공개 | spec.json + 생성한 source/definition | StandardSpec, StandardForms |
| 추가 질문 | 카탈로그 키 또는 custom id | 영상·나이·SNS·일정·예/아니오·소속 등. 외부 AI의 extras JSON을 운영자가 검토·등록 | spec와 펼친 definition | standard-v1.extras.json, StandardSpec |
| 배우 입력 | answers, photos, 지정 파일 이름 | 브라우저 메모리. 서버 요청 때 검사. 새로고침·탭 종료 시 복구용 저장 없음 | 브라우저 세션 메모리 | useAnswers, ApplyApp |
| 배우 작업 | 무작위 documentId, owner, expiresAt | 공유 원본 복사. 작업별 결과 분리. owner는 공고·버전/테스트 구분이며 배우 계정 인증이 아님 | 메모리 인덱스 + 임시 폴더, 생성 후 30분 | DocumentStore, StoredDocument, FormBuilder |
| 문서 생성 | JobContent: growths, writes, targets | 원본 사본에서 매번 생성. 줄 증감에 맞춰 쓰기 주소와 미리보기 주소 함께 계산 | completed.hwp와 임시 사진 | FormComposer, CompletedDocumentWriter |
| 가변 표·쪽 배치 | TableGrowth, TableFlow | 가로 병합 복원, 세로 병합·중첩 표 제약. rhwp 쪽 배치로 겹침 보정 시도 | 생성 결과에 반영 | RowInserter, TableFlow |
| 미리보기·PDF | 페이지 그림, hotspot, completed.pdf | 서버 rhwp 렌더링. 재생성 시 캐시 무효화. 미리보기 클릭으로 항목 수정 | 작업 폴더, 작업 만료까지 | PreviewService, PageLayout, PdfConverter |
| 파일 이름 | 다운로드 stem | 배우 수정 우선. HWP/PDF 같은 stem. 서버 실제 저장 경로에는 사용자 파일 이름을 사용하지 않음 | 작업 메타데이터 | CompletedFileName, form/fileName.ts |
| 제출 안내 | email, subject, deadline, note | 받는 곳·치환한 제목·마감·별도 첨부 안내. 마감일은 표시용 | 공용 definition → 배우 화면 | Submission, SubmissionGuide |
| 파일 전달 | File, 다운로드 URL, mailto | 표준 공고 PDF 우선. OS 공유 또는 저장 후 메일 작성. 서버 발송·접수 확인 없음 | 전달 상태 장기 저장 없음 | useDelivery, delivery |
| 외부 브라우저 이어받기 | /apply/{vid}?doc=작업ID | 완성 파일 이어받기. 공고 공개·마감 상태와 별개. 답은 이동하지 않아 편집 불가. 현재 PDF 우선·제출 안내는 이어받지 못함 | 작업 수명 안에서만 | ResumeNotice, shell, DocumentService.resume |
| 운영자 인증 | Bearer token | 관리자 API 공통 토큰. 사용자 계정·역할별 권한 없음 | 서버 설정, 브라우저 sessionStorage | AdminTokenFilter, adminApi |
| 테스트·공개 자격 | testedHash | 현재 원본+정의 SHA-256과 마지막 성공 테스트 지문 일치 필요 | version.json | FormBuilder.fingerprint, FormAdminService |
| 백업·복원 | 형식 1 ZIP, backup.json | 공고 데이터만 보관. 없는 vid만 복원, 기존 vid는 건너뜀 | 운영자가 받은 ZIP | FormBackup, BackupPanel |
| 통계·운영 제한 | 완성 횟수, GA4 화면 이벤트, IP별 요청 수 | 생성 횟수와 실제 지원 완료 구분. 관리자 테스트 제외. UUID 로그 마스킹 | 카운터·회전 로그·외부 분석 | CompletionCounter, analytics, RateLimitFilter, JobIds |

## 3. 관계와 상태 전이

| 관계 | 의미 |
|---|---|
| 공고 1 : N 양식 버전 | 원본·정의·표준 설정의 변경 이력 |
| 양식 버전 1 : N 배우 작업 | 같은 공고여도 각 방문자가 만든 결과는 독립 |
| 양식 정의 1 : N 항목 / N 출력 | item.id로 연결. 문서 주소는 배우가 정하지 않음 |
| 표준 설정 → 원본 HWP + 전체 정의 | 공고를 준비할 때 구체화. 배우 생성 시마다 표준 설정을 다시 펼치지 않음 |
| 작업 1 : 최신 완성 HWP / PDF / 미리보기 | 재생성은 해당 작업의 최신 결과를 교체. 제출 이력 저장소가 아님 |

| 대상 | 전이 | 조건·효과 |
|---|---|---|
| 양식 | 미등록 → 초안 | 원본 업로드 또는 정의·표준 설정 저장 |
| 양식 | 초안 → 테스트 성공 | 정의/원본 문제 없고 파일 생성 성공. 사람의 내용·레이아웃 확인은 별도 |
| 양식 | 테스트 성공 → 공개 | 원본+정의 지문 일치. 공개 API는 최신 공개 버전만 표시 |
| 양식 | 공개 중 수정 → 새 초안 | 이전 공개 버전은 유지. 새 버전 공개 전까지 신규 배우도 이전 버전 사용 |
| 공고 | 공개 → 종료 | 새 조회·생성 모두 차단. 이미 생성한 작업의 다운로드는 작업 만료까지 별도로 가능 |
| 공고 | 종료 → 재개 | 운영자 명시 동작. 날짜로 자동 처리하지 않음 |
| 작업 | 생성 → 완성 → 수정 완성 | 처음 완료만 집계. 30분 수명은 수정 때 연장하지 않음 |
| 작업 | 만료/서버 재시작 → 접근 불가 | 메모리 인덱스 복구 없음. 공고 화면에 답이 남아 있으면 생성 시 새 작업으로 재시도 |

## 4. 기본 수치와 표준 v1 범위

| 항목 | 현재 값 |
|---|---|
| 표준 사진 | 대표 1 + 자유 프로필 2 = 총 3칸. 대표만 필수 |
| 표준 필수 입력 | 이름·생년월일·연락처·성별·대표 사진 |
| 표준 경력 | 기본 문서 10줄, 입력 총 20줄까지, 5열 |
| 배우 자기 항목 | 항목 이름/내용 2열, 최대 3개 |
| rows 일반 제한 | 1~8열, maxRows 1~50, 칸당 100자, 빈 줄 제거 |
| 자기소개 | 1,500자. 쪽 수 2~3쪽 고정 보장 없음 |
| 영상 링크 | 현재 여러 줄 text, 600자. 3개까지 안내하지만 개수·URL 유효성 강제 검사 없음 |
| 문서 업로드 | HWP/HWPX 20MB, 요청 전체 60MB |
| 서버 사진 검사 | JPEG/PNG 12MB, 한 변 8,000px, 총 4천만 화소 |
| 요청 제한 기본값 | 공개 POST API IP당 10분 60회, 관리자 API 제외 |
| 작업·렌더링 | 작업 300개 기본 한도, rhwp 동시 2개, 슬롯 대기·개별 실행 각각 60초 제한 |
| 보관 | 작업 생성 후 30분 접근 만료, 5분 주기 정리. 정상 정리 지연·실패 재시도 가능 |

## 5. 배포·데이터 경계

Docker 한 컨테이너에 프런트 빌드, Spring Boot, hwplib, rhwp, 한글 폰트를 넣는다. 공고 원본·정의·설정은 /data/forms, 카운터와 로그도 /data 볼륨을 사용한다. 배우 작업은 /tmp/yesulin-actor와 서버 메모리에 둔다. 현재 구조는 단일 인스턴스 전제다.

백업 ZIP에는 공고의 state/source/definition/version/spec만 들어간다. 배우 입력·사진·완성본, 렌더 캐시, 운영자 토큰, 로그, 카운터는 포함하지 않는다. 백업 기능 구현과 실제 운영자가 최신 백업을 외부에 보관 중인지는 별개의 사실이다.

범위 밖: OTR 자동 수집·이미지 자동 해석·AI 정의 추론, DOCX/DOC/XLSX 엔진, 온라인 폼 제출, 영상 파일 수집, 서버 메일 발송, 배우 계정·장기 프로필, 공연사 심사·접수 관리.
