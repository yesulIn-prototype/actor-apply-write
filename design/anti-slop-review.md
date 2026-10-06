# anti-slop 도입과 작은 코드 개선

2026-10-04 사용자 요청으로 anti-slop을 실제 Oxlint에 연결하고, 앞서 제안한 두 코드만 최소 변경으로 개선했다. 다른 애플리케이션 파일의 진단을 자동 수정하거나 규칙을 완화하지 않았다.

## 도입 범위와 출처

- 원본: [dmmulroy/anti-slop](https://github.com/dmmulroy/anti-slop), 커밋 `c44ef22ca116d0ba62a3ff663a0bd13a3f3fa40b`.
- 저장소에 보관한 `design/skills/install-anti-slop/assets/anti-slop/` 전체를 `frontend/tools/oxlint/anti-slop/`로 복사했다. 번들 38개 파일의 SHA-256이 설치 스킬의 원본과 같음을 확인했다.
- 번들 루트 MIT `LICENSE`와 `UPSTREAM.md`를 추가했다. 기존 내부 Stylistic `LICENSE`·`UPSTREAM.md`는 보존했다. 규칙 코드는 수정하지 않았다.
- 기존 잠금 파일의 Oxlint 버전 `1.85.0`에 맞춰 `oxlint`와 `@oxlint/plugins`를 모두 정확히 `1.85.0`으로 고정했다. 다른 의존성 범위·잠금 버전은 유지했다.
- `frontend/.oxlintrc.json`의 기존 React 규칙을 보존하고 일반 플러그인 규칙 18개와 native `oxc/no-accumulating-spread`를 모두 오류로 추가했다. Effect 직접 의존성이 없어 Effect 플러그인은 등록하지 않았다.
- `frontend` 내부의 에이전트 디렉터리, `node_modules`, `dist`, 복사한 번들을 검사에서 제외했다. `design/skills`는 frontend 검사 루트 밖에 있다. Oxlint ignore 패턴은 상위 경로 `..`를 지원하지 않아 등록하지 않았다.
- 업데이트는 설치 스킬의 `references/update.md`를 따라 고정 커밋과 로컬 수정사항을 비교한다. 번들 원문을 무조건 덮어쓰지 않는다.

## 작은 개선

### 분석 큐의 이중 타입 단언 제거

`frontend/src/analytics.ts`에서 `Window.dataLayer?: unknown[]`를 선언하고 `window.dataLayer`에 직접 접근한다. 공유 큐에 기존 값이 있을 수 있어 큐 타입을 `IArguments[]`로 좁히지 않았다. GA4가 사용하는 `arguments` 객체 push, 운영 호스트 제한, 기존 큐 재사용과 중복 초기화 방지는 유지했다. 기존 비운영 호스트 테스트도 같은 타입 선언을 사용한다.

운영 호스트로 설정한 별도 jsdom 테스트를 추가해 다음을 확인했다.

- 두 번 초기화해도 스크립트가 하나이고 명령은 3개이며, 각 명령이 `Arguments` 객체다.
- 실제 작업 주소의 `doc` 값은 보고 주소에 포함하지 않는다.
- 페이지뷰 자동 전송·Google signals·광고 개인화 설정은 기존대로 꺼져 있다.
- 공유 큐가 이미 있으면 배열의 identity와 기존 내용이 유지된다.

### 문서 영역 번호의 반복 정렬 제거

`frontend/src/document-editing/api.ts`의 `numberRegions`는 영역마다 첫 박스를 한 번 계산해 정렬용 데이터에 보관한다. 기존 박스 정렬 비교식을 그대로 사용했다. 영역 비교나 결과 번호 생성 때 박스 배열을 다시 정렬하지 않는다. 빈 영역 제외, `page → y → x → id` 순서, 페이지별 번호 재시작, 원본 영역·박스 배열은 유지한다.

추가 테스트는 뒤섞인 다중 페이지 박스, 좌표 동률의 id 순서, 페이지 번호 간격, 빈 영역, 동결한 원본 배열과 박스 순서를 확인한다. 기존 브라우저 대상 `safari14`·`chrome87`을 유지하며 iterator helpers나 `toSorted()`는 도입하지 않았다. 처리 시간 향상은 벤치마크하지 않았으므로 주장하지 않는다.

## 검증 결과

| 검증 | 실제 결과 |
|---|---|
| 변경 전 관련 테스트 | 3개 파일, 12개 테스트 통과. 새 동작 보존 테스트를 기존 코드에서 먼저 실행했다. |
| 새 타입 접근의 변경 전 타입 검사 | `Window.dataLayer` 선언이 없어 TS2339로 종료 코드 2. 타입 선언 후 종료 코드 0. |
| 잠금 파일 재설치 | `npm ci --ignore-scripts` 성공. Oxlint와 plugins 설치 버전 모두 1.85.0 확인. |
| 최종 전체 프런트 테스트 | `npm test`: 13개 파일, 45개 테스트 통과, 종료 코드 0. |
| 타입 검사와 빌드 | `npm run build` (`tsc -b && vite build`): 종료 코드 0. |
| 전체 lint | `npm run lint -- --format json`: 57개 파일, 오류 257건·경고 0건, 종료 코드 1. 아래 분류 참조. |
| 외부 번들 보존 | 38개 원본 파일 해시 일치. |
| 변경 내용 | 애플리케이션 코드 2개와 관련 테스트만 수정. 백엔드 코드와 UI 구조·스타일 변경 없음. |

명령은 이전 검증에서 확인한 샌드박스의 임시 파일·gitignore 접근 문제를 피하도록 제한 밖에서 실행했다. 실제 브라우저·휴대폰·한글 앱 QA와 백엔드 재검증은 이번 작업에서 수행하지 않았다. UI·문서 생성 코드를 바꾸지는 않았지만 전체 고객 흐름 검증을 대신했다고 주장하지 않는다.

## 남은 진단

다음 수치는 최종 명령의 오류 건수다. 도구의 기본 정책과 현재 코드 관례의 차이도 포함하므로 **257개 런타임 버그라는 뜻이 아니다**. 규칙 강도를 낮추거나 광범위한 자동 수정을 하지 않았다.

| 규칙 | 건수 | 대표 위치와 판단 |
|---|---:|---|
| `require-readable-spacing` | 220 | 여러 소스·테스트의 선언·제어문 사이 빈 줄. 별도 서식 변경으로 분리할 수 있다. |
| `no-runtime-typeof` | 16 | `document-editing/api.ts`의 편집 응답 파서. 현재도 경계에서 구조를 확인하지만 anti-slop은 이 검사 방식 자체를 허용하지 않는다. 파서 정책 결정 후 정리해야 한다. |
| `require-safety-comment-for-type-assertion` | 8 | `api.ts`, `admin/adminApi.ts`, `form/api.ts`, `ApplyApp.test.tsx`의 타입 단언. 실제 경계 검증과 반환 타입 근거를 확인해야 한다. |
| `no-unknown-parameters` | 5 | 편집 응답 파서와 `shell.ts` 오류 메시지 경계. 의도적인 외부 입력 처리가 포함되므로 단순 타입 교체로 숨기지 않는다. |
| `no-known-value-widening` | 4 | 메시지 사전·헤더 반환·답변 사전의 넓은 `Record` 타입. 각 반환 계약과 소비자를 확인해 좁힐 수 있다. |
| `no-chained-type-assertions` | 1 | `ApplyApp.test.tsx`의 남은 이중 단언. 이번 분석 큐 개선 범위 밖이다. |
| `no-array-filter-map` | 1 | `form/fileName.ts`의 문자열 정리 `map` → `filter`. 지원 브라우저와 공백 제거·빈 값 제외 의미를 보존해야 한다. |
| `no-conditional-empty-object-spread` | 1 | `form/api.ts`의 선택적 필드 추가. 필드 생략과 `undefined` 대입이 같은지는 요청 직렬화 계약으로 확인해야 한다. |
| `no-module-mocking` | 1 | `useDelivery.test.tsx`의 모듈 모킹. 실제 브라우저 의존 경계를 검토해야 하므로 테스트만 기계적으로 바꾸지 않는다. |

## 도입 당시 CI 영향과 다음 범위 (2026-10-04)

도입 당시 `npm run lint`는 실제로 실패했다. `.github/workflows/ci.yml`의 프런트 작업도 같은 lint 명령을 테스트·빌드 전에 실행하므로 남은 오류가 CI를 막는 상태였다. 당시 작업에서는 원격 CI 실행·커밋·푸시·배포를 하지 않았다.

후속 작업은 서식 정리와 경계 타입·파서·테스트 정책 정리를 분리하는 것이 적합하다. 파서 라이브러리 도입이나 모듈 모킹 대체 구조는 새 설계 결정이므로 자동 확정하지 않는다. 다음 실행에서 `npm run lint -- --format json`으로 당시 파일·줄 번호를 다시 확인한다.

## CI 실패 수정 (2026-10-06)

[실패 실행 37404498430](https://github.com/yesulIn-prototype/actor-apply-write/actions/runs/37404498430)의 실제 로그를 확인했다. 대상 커밋은 `a296bff`다.

- 프런트는 lint 오류 249개로 중단되어 테스트·빌드 단계가 실행되지 않았다. 빈 줄 서식, 응답 타입 단언, 편집 응답의 수동 타입 검사, 테스트 모듈 모킹 등이 원인이었다. 규칙·CI 단계·외부 플러그인은 유지하고 코드를 정리했다.
- 기존 의존성 Zod로 공고·운영자·미리보기·편집 응답을 경계에서 검증한다. 공유 테스트는 모듈 모킹 대신 실제 공유 함수와 브라우저 `navigator.share` 경계를 사용한다. 파일명 처리의 문자열 정리 결과와 기존 브라우저 대상은 유지한다.
- `parseEditView`의 입력은 검증 전 JSON이므로 `unknown`을 유지했다. 이 함수의 입력에만 `no-unknown-parameters` 예외를 이유와 함께 명시하고, 반환값은 스키마로 검증한다. 전역 규칙이나 파일 전체를 제외하지 않았다.
- 백엔드는 편집 테스트 두 파일이 `rhwp.exe`를 직접 지정해 Ubuntu에서 5개가 `PdfUnavailableException`으로 실패했다. 다른 렌더링 테스트·운영 설정과 같은 확장자 없는 `rhwp` 경로를 사용하도록 바꾸었다. Windows에서는 기존 `PdfConverter`가 `.exe`를 붙인다. 테스트나 렌더링 검사를 건너뛰지 않았다.

이번 작업의 새 검증 결과:

| 검증 | 결과 |
|---|---|
| `npm run lint` | 종료 코드 0 |
| `npm test` | 19개 파일, 71개 테스트 통과 |
| `npm run build` | 타입 검사·Vite 빌드 통과 |
| Windows `gradlew.bat --no-daemon test` | 92개, 실패·오류·건너뜀 0 |
| 격리된 Ubuntu, 공식 Temurin Java 25·rhwp v0.8.6, `./gradlew --no-daemon test` | 92개, 실패·오류·건너뜀 0 |

커밋·푸시·운영 배포는 수행하지 않았다. 수정된 커밋의 GitHub CI 성공과 Railway 반영은 푸시 이후 별도로 확인해야 한다.
