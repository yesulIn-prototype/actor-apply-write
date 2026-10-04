# 프로젝트 에이전트 스킬

`AGENTS.md`와 `CLAUDE.md`가 함께 참조하는 외부 스킬 안내다. 전역 설정에 의존하지 않고 이 저장소의 `design/skills/`에 원문과 부속 파일을 보관한다. 작업을 시작할 때 이 문서와 `project-rules.md`를 읽고, 아래 조건에 해당하는 스킬 원문을 연다.

## 적용 조건

| 요청한 스킬 | 프로젝트 원문 | 적용할 때 |
|---|---|---|
| Superpowers: verification-before-completion | [SKILL.md](skills/verification-before-completion/SKILL.md) | 완료·수정 성공·테스트/빌드/lint 통과 보고 전. 해당 검증을 새로 실행하고 전체 출력·종료 코드·실패 수를 확인한다. 커밋·PR도 먼저 검증한다. |
| anti-slop: install-anti-slop | [SKILL.md](skills/install-anti-slop/SKILL.md) | anti-slop Oxlint 플러그인의 설치·설정·업데이트를 요청받았을 때. 기존 설정과 로컬 수정사항을 먼저 확인한다. |
| Andrej Karpathy: karpathy-guidelines | [SKILL.md](skills/karpathy-guidelines/SKILL.md) | 코드 작성·리뷰·리팩터링 전. 가정을 밝히고, 검증 가능한 목표를 정하며, 요청에 필요한 최소 변경만 한다. |
| attention-span | [attention-kind/SKILL.md](skills/attention-span/attention-kind/SKILL.md) | 응답을 작성할 때 기본 참고. 결론부터 짧고 쉽게 쓰되 필요한 근거·수치·조건·미확인 사항을 빠뜨리지 않는다. |

첫 항목은 사용자가 첨부한 `verification-before-completion`만 설치했다. Superpowers 전체 워크플로·플러그인·훅은 설치 범위에 포함하지 않는다.

anti-slop은 일반 코딩 지침만 담긴 스킬이 아니라 Oxlint 플러그인을 설치하는 절차다. 설치 스킬, `references/update.md`, `scripts/install.mjs`, `assets/anti-slop/`를 함께 보관한다. 2026-10-04 사용자 요청으로 실제 플러그인을 `frontend/tools/oxlint/anti-slop/`에 설치했고, 기존 Oxlint에 일반 규칙을 오류 수준으로 연결했다. 적용 결과와 남은 진단은 [anti-slop-review.md](anti-slop-review.md)에 기록한다. 원문을 읽는 것만으로 설치·업데이트 스크립트를 다시 실행하지 않는다.

attention-span의 원본 스킬은 사용자 호출용(`disable-model-invocation: true`)이다. 이 프로젝트에서는 위 표에 따라 `attention-kind`를 응답 지침으로 참고하며, 다른 스타일을 동시에 적용하지 않는다. 사용자에게 ADHD가 있다고 가정하지 않는다. 영어 지시 대신 한국어로 답하고, 자세한 설명 요청과 필수 검증을 생략하지 않는다. 다음 스타일은 사용자가 선택하거나 요약을 요청했을 때 해당 원문을 읽는다.

- [spartan/SKILL.md](skills/attention-span/spartan/SKILL.md): 더 짧고 직접적인 응답을 요청했을 때.
- [rundown/SKILL.md](skills/attention-span/rundown/SKILL.md): 진행 상태를 요약·체크 목록으로 요청했을 때.
- [tldr/SKILL.md](skills/attention-span/tldr/SKILL.md): 사용자가 지정한 문서·대화·본문을 요약할 때. 원문에 없는 상태나 결론을 만들지 않는다.

## 출처와 버전

2026-10-04에 조회한 각 저장소의 HEAD 커밋으로 고정했다. 원문·부속 파일은 번역하거나 수정하지 않았으며, 프로젝트별 적용 조건은 이 문서에만 적었다.

| 출처 | 고정 커밋 | 가져온 경로 | 라이선스 보존 위치 |
|---|---|---|---|
| [obra/superpowers](https://github.com/obra/superpowers) | `8ca22dba9a94f28898bbce59f2537ff4d87c747d` | `skills/verification-before-completion/` | `skills/verification-before-completion/LICENSE` (MIT) |
| [dmmulroy/anti-slop](https://github.com/dmmulroy/anti-slop) | `c44ef22ca116d0ba62a3ff663a0bd13a3f3fa40b` | `skills/install-anti-slop/` 전체 | `skills/install-anti-slop/LICENSE` (MIT), 번들 내부 Stylistic `LICENSE`·`UPSTREAM.md` |
| [multica-ai/andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills) | `2c606141936f1eeef17fa3043a72095b4765b9c2` | `skills/karpathy-guidelines/` | 원문 `SKILL.md`의 `license: MIT` 표기 보존. 이 커밋에 별도 LICENSE 파일은 없다. |
| [alexgreensh/attention-span](https://github.com/alexgreensh/attention-span) | `2714c965e6be1fa2597510e66651e63bc67cb448` | `skills/attention-kind/`, `skills/spartan/`, `skills/rundown/`, `skills/tldr/` | `skills/attention-span/LICENSE` (AGPL-3.0) |

## 갱신과 확인

1. 갱신 요청이 있을 때 새 커밋과 변경 내용을 먼저 확인한다. 스킬 내부 스크립트나 훅을 자동 실행하지 않는다.
2. 별도 임시 위치로 새 원문·부속 파일을 가져와 비교한 뒤 반영한다. 기존 디렉터리에 무조건 덮어쓰지 않는다.
3. 위 표의 커밋과 라이선스를 함께 갱신하고, 두 진입 문서에서 같은 안내로 연결되는지 확인한다.
4. 모든 로컬 참조 경로, `SKILL.md`의 이름·설명, 부속 파일과 원본 커밋의 일치를 확인한다. `git diff --check`와 변경 목록을 검토한다. 애플리케이션 코드나 도구 설정까지 바꾸었다면 `project-rules.md`의 해당 테스트·빌드·lint도 실행한다.

이 경로는 프로젝트 문서를 통한 명시적 참조 방식이다. 전역 스킬 등록이나 Claude 플러그인·슬래시 명령 등록은 하지 않는다. 다음 작업부터 두 도구가 진입 문서를 읽고 같은 스킬을 참조한다.
