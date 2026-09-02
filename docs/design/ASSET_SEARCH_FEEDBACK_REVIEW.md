# 종목 검색 피드백 레이아웃 검토

작업 계약: `docs/agent-tasks/claude-asset-search-feedback-review.md` · 작업 모드 `design` · 브랜치 `fix/asset-search-feedback-layout`

## 이 문서의 지위

이 문서는 **검토 제안**이다. 채택된 제품 결정이 아니다. 각 권고의 반영 여부는
Codex가 판단한다 (`docs/AI_COLLABORATION_POLICY.md` 24-26행). 이 검토는 API
계약, 데이터 모델, 투자 전략 정책에 영향을 주지 않는다.

## 검토 대상

- `frontend/src/components/asset/AssetSearchInput.tsx`
- `frontend/src/App.css`의 `.asset-search*`, `.search-message` 규칙
- 사용 위치: `frontend/src/pages/TradeTransactionEntryPage.tsx`의 `거래 정보`
  섹션, `.form-grid` 2열 레이아웃 안에서 시장/거래유형/체결시각 필드와 함께
  배치된다.

커밋 `093af37`(`fix: stabilize asset search feedback layout`)이 반영한
`.asset-search-feedback` 래퍼 도입과 `.asset-search-results { top: 68px }`
고정 앵커를 기준으로 확인했다.

## 상태별 확인 결과

| 상태 | 확인 결과 |
| --- | --- |
| 빈 입력(대기) | `showResults`가 false라 메시지·목록 모두 미노출. 다만 아래 "P0" 항목 참고 |
| 로딩 | `종목을 검색하는 중입니다.` 노출, `aria-live="polite"` 래퍼로 감쌈 |
| 결과 없음 | 안내 문구 노출, 로딩/에러와 상호 배타적이라 목록과 겹치지 않음 |
| 에러 | `role="alert"` 문단 노출. 아래 "P2" 항목 참고 |
| 결과 목록 | `ul#resultsId`가 절대 위치로 렌더링, 로딩/에러/빈 상태와 동시에 나타나지 않음 |

## 발견 사항

### P0 · 대기 상태에서도 피드백 영역이 항상 24px를 점유해 형제 필드와 높이가 어긋남

`.asset-search-feedback` div는 내용과 무관하게 항상 렌더링되고,
`min-height: 17px; margin-top: 7px`(App.css:241-244)로 24px를 고정 확보한다.
이번 커밋 이전에는 `search-message`가 조건부로만 렌더링되어 대기 상태에서는
추가 높이가 없었다.

`TradeTransactionEntryPage.tsx`의 `거래 정보` 섹션은 `.form-grid`
2열 그리드에서 `[시장, 거래유형]` / `[종목 검색, 체결 시각]` 두 행으로
배치된다. 종목 검색을 아직 사용하지 않은 대기 상태에서도 `종목 검색` 셀은
`체결 시각` 셀보다 24px 더 커지고, 그리드 행 높이는 더 큰 셀 기준으로
늘어난다. 결과적으로 폼을 처음 열었을 때나 종목을 이미 선택해 검색을 더 이상
쓰지 않는 상태에서도, 두 번째 행 아래에 불필요한 여백이 항상 남는다.

레이아웃 흔들림(CLS) 방지라는 원래 의도는 "검색이 활성화된 동안" 로딩→결과없음→에러
전환 시에만 필요하다. 현재 구현은 그 목적보다 넓게, 검색을 시작하지도 않은
상태까지 공간을 예약해 버렸다.

제안: 예약 공간을 `showResults`가 true일 때만 적용한다. 예를 들어
`className={`asset-search-feedback${showResults ? " is-active" : ""}`}`로
분기하고 `.asset-search-feedback.is-active`에만 `min-height`를 주거나,
`showResults`가 false면 래퍼 자체를 렌더링하지 않고 `aria-live` 영역만 별도
빈 요소로 유지하는 방식을 검토한다.

### P1 · `.asset-search-results { top: 68px }`가 라벨/입력의 실제 렌더 높이에 종속된 매직 넘버

`top: 68px`은 `종목 검색` 라벨(14px, font-weight 700) + `gap: 8px` + 입력
필드(패딩 10px×2 + 테두리)의 현재 렌더 높이를 역산한 값으로 보인다.
`.asset-search`(컨테이너) 기준 절대 위치라 다음과 같은 변화에 취약하다.

- 브라우저 확대/축소, OS 접근성 글자 크기 확대로 라벨·입력 실제 높이가 바뀌면
  드롭다운이 입력 아래에서 겹치거나 눈에 띄게 떨어진다.
- 추후 라벨 문구가 길어져 줄바꿈되거나, 입력 패딩/폰트가 다른 값으로
  조정되면 같은 문제가 재발한다.
- 이 값이 어긋나도 빌드나 lint로 잡히지 않아, 조용히 깨질 수 있다.

제안: 드롭다운을 `.asset-search` 전체가 아니라 `label` 내부의 입력 래퍼
기준 `top: 100%`로 앵커링하거나, 입력만 감싸는 별도 `position: relative`
컨테이너를 두어 라벨 높이와 무관하게 위치를 계산하도록 구조를 바꾼다.

### P1 · `aria-expanded`/`aria-controls`가 로딩·에러 상태를 반영하지 못함

`aria-expanded={showResults && results.length > 0}`는 로딩 중이거나 에러가
떠 있을 때(둘 다 `results.length === 0`) 항상 `false`다. 화면에는 피드백
문구가 보이지만 스크린리더 사용자에게는 "펼쳐지지 않음"으로 전달된다.
`aria-controls={resultsId}`도 이 상태들에서는 대상 `<ul>`이 DOM에 없는
id를 가리킨다.

제안: `aria-expanded`는 결과 유무가 아니라 `showResults`(피드백 영역이
열려 있는지) 기준으로 바꾸고, 로딩/에러/빈 결과 메시지 쪽에도 입력과
연결되는 id를 주어 `aria-describedby`로 연결하는 방안을 다음 구현 슬라이스에서
검토한다.

### P2 · `aria-live="polite"` 래퍼 안에 `role="alert"` 문단이 중첩됨

`.asset-search-feedback`은 항상 `aria-live="polite"`이고, 에러 문구에는
개별적으로 `role="alert"`(암묵적 assertive)가 붙어 있다. 정중 라이브 리전
안에 단정적 역할이 중첩되면 스크린리더/브라우저 조합에 따라 중복 안내되거나
반대로 무시되는 등 동작이 일관되지 않을 수 있다. 셋 중 하나만 선택하는 편이
안전하다: 래퍼의 `aria-live="polite"`만 유지하고 내부 `role="alert"`을
제거하거나, 에러 상태에서만 래퍼의 `aria-live`를 `assertive`로 전환한다.

### P2 · 결과 목록에 키보드 콤보박스 패턴이 없음 (이번 커밋 범위 밖, 다음 슬라이스 후보)

현재 결과는 `<button>` 목록으로만 존재해 방향키 탐색, `Enter` 선택,
`aria-activedescendant` 연동이 없다. 키보드 사용자는 각 결과를 `Tab`으로
하나씩 지나쳐야 다음 폼 필드(`체결 시각`)에 도달한다. 또한 바깥 영역 클릭이나
`blur`로 닫히는 처리가 없어 `Escape` 키만이 유일한 닫기 수단이다. 이번
레이아웃 안정화 커밋이 만든 문제는 아니지만, "키보드·스크린리더 피드백
동작 확인"이라는 검토 기준에 해당하는 기존 격차이므로 함께 기록한다.

## 우선순위 요약

| 우선순위 | 항목 | 이번 커밋 도입 여부 |
| --- | --- | --- |
| P0 | 대기 상태에서도 24px 고정 예약되는 피드백 영역 | 예 (093af37에서 도입) |
| P1 | 드롭다운 `top: 68px` 매직 넘버 | 예 (068px 하드코딩은 이번 커밋에서 추가) |
| P1 | `aria-expanded`/`aria-controls`가 로딩·에러 상태 미반영 | 아니오 (기존 로직, 변경 없음) |
| P2 | `aria-live="polite"` 안 `role="alert"` 중첩 | 부분적 (래퍼는 새로 추가, 중첩 자체는 기존) |
| P2 | 결과 목록 키보드 콤보박스 패턴 부재 | 아니오 (기존 격차) |

## 다음 구현 슬라이스 제안

1. P0을 가장 먼저 반영한다 — 시각적 회귀이며 수정 범위가 작다(`showResults`
   조건부 클래스/렌더링 하나).
2. P1 두 건은 함께 처리할 수 있다 — 드롭다운 앵커 구조를 바꾸는 김에
   `aria-expanded` 기준도 함께 정리한다.
3. P2 두 건은 별도 접근성 개선 작업(콤보박스 패턴 도입)으로 묶어 처리하는
   편이 자연스럽다.

## 검증 방법

정적 코드 검토와 `App.css`/`AssetSearchInput.tsx`/`TradeTransactionEntryPage.tsx`
교차 확인으로 수행했다. 프런트엔드 빌드나 브라우저 수동 실행은 하지 않았다
(이 작업 계약은 `docs/design/ASSET_SEARCH_FEEDBACK_REVIEW.md` 외 파일 쓰기를
허용하지 않으며, `frontend/`는 읽기 전용 컨텍스트로만 사용했다).
