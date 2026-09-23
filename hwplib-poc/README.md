# hwplib 비교 PoC

이 디렉터리는 `kordoc`가 실제 HWP 지원서의 텍스트와 사진 수정 조건을 충족하지 못한 뒤 수행한 `hwplib 1.1.11` 비교 실험이다.

## 범위

- 실제 HWP 읽기 및 무수정 저장
- 표 셀 좌표 기반 단일/다중 텍스트 입력
- 사진 셀 문단에 이미지 채움 사각형 삽입
- 저장 후 `hwplib` 재열기
- `kordoc` 재파싱 및 렌더 교차 검증

웹 UI, API, 자동 필드 매퍼는 포함하지 않는다.

## 소스

- `HwpApplicantDocument.java`: HWP 읽기, 표/셀 접근, 텍스트 수정, 저장 경계
- `CellImageInserter.java`: 지정 셀의 이미지 바이너리와 이미지 채움 도형 삽입
- `HwpApplicantDocumentTest.java`: 실제 지원서를 사용하는 실행형 회귀 테스트

## 검증 결과

실행형 테스트는 다음을 통과했다.

- 표 2개와 행 구조 인식
- 무수정 저장 후 재열기
- 단일 텍스트 입력 후 재열기
- 다중 필드 입력 후 재열기
- 사진 1장 삽입 후 임베디드 이미지 수 증가
- 사진 4장 삽입 후 임베디드 이미지 수 증가

사진의 실제 화면 표시 상태는 사용자가 `output/multi-photo-test.hwp`를 한글에서 열어 확인했다.

전체 판정과 증거는 `../kordoc-test/verification.md`에 기록했다.
