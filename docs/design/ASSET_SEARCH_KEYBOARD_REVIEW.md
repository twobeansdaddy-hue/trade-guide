# 종목 검색 키보드 UX 검토

작업 계약: `docs/agent-tasks/claude-asset-search-keyboard-review.md` · 작업 모드 `design` · 브랜치 `fix/asset-search-feedback-layout`

## 이 문서의 지위

이 문서는 **검토 제안**이다. 채택된 제품 결정이 아니다. 각 권고의 반영 여부는
Codex가 판단한다 (`docs/AI_COLLABORATION_POLICY.md` 24-26행). 이 검토는 API
계약, 데이터 모델, 투자 전략 정책에 영향을 주지 않으며, `frontend/**`는 읽기
전용 컨텍스트로만 사용했다. 코드 변경은 포함하지 않는다.

## 검토 대상 (현재 코드 기준)

- `frontend/src/components/asset/AssetSearchInput.tsx`
- `frontend/src/App.css`의 `.asset-search*`, `.search-message` 규칙
- `frontend/src/pages/TradeTransactionEntryPage.tsx`의 `거래 정보` 섹션,
  `.form-grid` 2열 레이아웃 안에서 시장/거래유형/체결시각 필드와 함께 배치됨
- 선행 문서 `docs/design/ASSET_SEARCH_FEEDBACK_REVIEW.md`

선행 문서가 지적한 P0(대기 상태 고정 높이)와 P1(`top: 68px` 매직 넘버,
`aria-expanded`가 로딩·에러를 반영하지 못하던 문제)은 현재 코드에서 이미
해소되어 있음을 확인했다: `.asset-search-feedback`은 더 이상 `min-height`를
갖지 않고, `.asset-search-results`는 `.asset-search-input-wrap` 기준
`top: 100%`로 앵커링되며, `aria-expanded={showResults}`로 바뀌어 있다. 다만
`role="alert"` 중첩 여부와 무관하게, **이번 검토의 핵심 대상인 "결과 목록
키보드 콤보박스 패턴 부재"는 선행 문서의 P2 항목 그대로 남아 있다.** 이 문서는
그 격차를 구체화한다.

## 현재 동작 요약

- 입력에서 `Escape`만 처리한다 (`setIsSearchOpen(false)`). `ArrowDown`,
  `ArrowUp`, `Enter`에 대한 처리가 없다.
- 결과는 `<ul><li><button>` 목록으로 렌더링되며, 각 버튼은 독립적으로
  포커스를 받는 일반 탭 순서상의 요소다. 키보드 사용자는 목록에 진입하려면
  `Tab`으로 입력 필드를 벗어나 버튼을 하나씩 지나쳐야 하고, 다음 폼 필드
  (`체결 시각`)로 가려면 결과 개수만큼 `Tab`을 더 눌러야 한다.
- `AssetSearchInput`은 `<form onSubmit={handleSubmit}>` 내부에 있다
  (`TradeTransactionEntryPage.tsx`). 입력 필드에서 `Enter`를 누르면 현재
  아무 것도 가로채지 않으므로 브라우저 기본 동작대로 **폼이 제출된다.**

## 확인 질문에 대한 답

### 1. `ArrowDown`/`ArrowUp`/`Enter`/`Escape` 동작

결과 목록이 열려 있고(`showResults`) 항목이 1개 이상일 때를 기준으로:

- `ArrowDown`: 강조(highlight) 인덱스를 다음 항목으로 이동. 마지막 항목에서
  더 누르면 그 자리에 멈춘다(순환 없음, 아래 5번 참고). 목록이 닫혀 있고
  입력값이 있는 상태에서 누르면 목록을 다시 연다.
- `ArrowUp`: 강조 인덱스를 이전 항목으로 이동. 첫 항목에서 멈춘다.
- `Enter`: 강조된 항목이 있으면 해당 종목을 선택(`selectAsset`)하고
  드롭다운을 닫는다. **이때 반드시 `preventDefault()`로 폼 제출을 막아야
  한다** — 이 필드는 `TradeTransactionEntryPage`의 `<form>` 안에 있으므로,
  막지 않으면 종목을 고르려던 `Enter`가 아직 다른 필드(수량·단가 등)를
  채우지 않은 폼을 그대로 제출해버린다. 강조된 항목이 없으면(목록이
  닫혀 있거나 결과가 없음) 기본 동작을 막지 않는다 — 사용자가 폼을 완성한
  뒤 `Enter`로 제출하는 기존 흐름을 그대로 둔다.
- `Escape`: 드롭다운을 닫는다(기존 동작 유지). 강조 인덱스도 함께 초기화한다.
  입력값 자체는 지우지 않는다. 포커스는 입력 필드에 그대로 둔다.

### 2. 선택 후 포커스 위치

입력 필드에 그대로 둔다. 현재 구현도 마우스 클릭 시 버튼이 포커스를
가져가지 않는 한(브라우저별 기본 동작 차이 있음) 입력에 포커스가 남는
구조이므로, 키보드 선택도 동일하게 "가상 포커스"만 이동시키고 실제 DOM
포커스는 계속 입력 필드에 두는 방식이 자연스럽다. 종목명을 고른 뒤 사용자가
바로 다음 필드(`체결 시각`)로 `Tab`할 수도 있고, 티커를 다시 확인하려 할
수도 있으므로 임의로 다음 필드로 포커스를 이동시키지 않는다.

### 3. 결과 없음/오류 시 키보드 동작 제한

`results.length === 0`(로딩 중, 오류, 빈 결과 메시지 모두 포함)인 동안은
`ArrowDown`/`ArrowUp`이 강조할 대상이 없으므로 아무 동작도 하지 않는다(값
변경 없이 조용히 무시). `Enter`도 강조된 항목이 없으므로 선택 로직을 타지
않고 기존 폼 제출 동작을 그대로 둔다. `Escape`는 이 상태에서도 동일하게
드롭다운(피드백 영역 포함)을 닫아야 한다 — 로딩 메시지나 오류 메시지가 떠
있는 상태에서 벗어날 유일한 키보드 수단이기 때문이다.

### 4. 최소 ARIA 속성 및 포커스 처리

전체 콤보박스 패턴(WAI-ARIA 1.2 combobox)을 다 구현하지 않고, 이번 제품
규모에 맞는 최소 조합만 권고한다.

- 입력: `role="combobox"`, `aria-autocomplete="list"`,
  `aria-haspopup="listbox"`, 기존 `aria-expanded={showResults}` 유지,
  `aria-controls={resultsId}`는 목록이 실제로 DOM에 있을 때만 지정(현재
  로직 유지), `aria-activedescendant`를 강조된 항목의 `id`로 지정(강조 항목이
  없으면 속성 자체를 생략).
- 목록: `<ul>`에 `role="listbox"` 추가(`id`, `aria-label`은 기존 유지).
- 각 항목: `<li>` 또는 옵션 역할을 갖는 요소에 `role="option"`,
  `id={`${resultsId}-option-${index}`}` 같은 안정적 id, `aria-selected`를
  강조 여부로 설정.
- 포커스 이동 없음: 실제 DOM 포커스는 입력 필드에 유지하고, "가상 포커스"는
  `aria-activedescendant` + 시각적 강조 클래스만으로 표현한다. 이는 스크린
  리더 사용자가 매 항목마다 포커스 이동 알림을 듣지 않고 입력 필드에 머무른
  채 옵션을 탐색하는 표준 콤보박스 동작과 일치한다.
- 마우스 클릭이 버튼에 포커스를 가져가면(브라우저에 따라 다름)
  `aria-activedescendant`와 실제 DOM 포커스가 어긋날 수 있으므로,
  `onMouseDown`에서 `preventDefault()`로 버튼이 포커스를 받지 않게 하고
  `onClick`으로 선택을 처리하는 편이 안전하다.
- 시각적 강조: 현재 `.asset-search-results button:focus-visible` 스타일은
  실제 DOM 포커스가 버튼에 가는 경우에만 적용된다. 콤보박스 패턴에서는
  버튼이 포커스를 받지 않으므로, `aria-selected="true"` 또는 별도 클래스
  기준의 새 스타일이 필요하다(이 문서는 `App.css` 수정 범위가 아니므로
  구현 시 함께 반영 필요하다는 점만 기록한다).

### 5. 이번 슬라이스에서 제외할 고급 패턴

- **순환(wrap-around) 탐색**: 마지막 항목에서 `ArrowDown` 시 첫 항목으로
  돌아가는 동작. 없어도 사용성에 큰 지장이 없고, 클램프(경계에서 멈춤)보다
  구현·테스트 부담이 크다.
- **타이프어헤드 자동완성(ghost text)**: `aria-autocomplete="both"`로 입력
  중 첫 매칭 결과를 회색 텍스트로 미리 채우는 패턴. 커서 위치·IME(한글
  종목명) 입력과 충돌 여지가 커서 별도 검토가 필요하다.
- **바깥 클릭/블러로 닫기**: 마우스 기반 동작이라 이번 키보드 검토의
  핵심 범위 밖이다. 선행 문서(`ASSET_SEARCH_FEEDBACK_REVIEW.md` P2)에 이미
  별도 격차로 기록되어 있으므로 여기서 중복 정의하지 않는다.
- **`Home`/`End`/`PageUp`/`PageDown`으로 첫/마지막/페이지 단위 이동**: 결과가
  최대 몇 건 수준으로 짧다면(스크롤 영역 `max-height: 220px`) 우선순위가
  낮다.
- **마우스 호버와 키보드 강조 상태의 양방향 동기화**: 호버 시 강조 인덱스를
  갱신하는 것은 가능하지만 필수는 아니며, 마우스와 키보드 강조가 서로
  덮어쓰며 깜빡이는 부작용을 만들 수 있어 제외를 권고한다.

## 권장 키보드 동작 표

| 키 | 목록 열림 + 결과 있음 | 목록 열림 + 결과 없음/로딩/오류 | 목록 닫힘 |
| --- | --- | --- | --- |
| `ArrowDown` | 강조 인덱스 +1 (마지막에서 정지), `preventDefault` | 무시 | 입력값이 있으면 목록을 열고 첫 항목 강조 대기 |
| `ArrowUp` | 강조 인덱스 -1 (첫 항목에서 정지), `preventDefault` | 무시 | 무시 |
| `Enter` | 강조 항목 선택 + 드롭다운 닫기, `preventDefault`(폼 제출 방지) | 무시(폼 제출 등 기존 동작 유지) | 무시(기존 동작 유지) |
| `Escape` | 드롭다운 닫기 + 강조 인덱스 초기화 | 드롭다운(피드백 영역) 닫기 | 무시(이미 닫힘) |

## 최소 접근성 계약

1. 입력에 `role="combobox"`, `aria-autocomplete="list"`,
   `aria-haspopup="listbox"`를 추가하고, 기존 `aria-expanded`/`aria-controls`/
   `aria-describedby`는 유지한다.
2. 강조된 항목이 있을 때만 입력에 `aria-activedescendant`를 설정한다.
3. 결과 목록에 `role="listbox"`, 각 항목에 `role="option"` + 안정적 `id` +
   `aria-selected`를 부여한다.
4. 실제 DOM 포커스는 검색 과정 전체에서 입력 필드를 벗어나지 않는다.
5. 로딩/빈 결과/오류 메시지의 기존 `aria-live="polite"` + `aria-describedby`
   연결은 그대로 유지한다(이번 검토 범위 밖).

## 우선순위 요약

| 우선순위 | 항목 | 이유 |
| --- | --- | --- |
| P0 | `Enter` 키에서 강조 항목 선택 시 `preventDefault`로 폼 제출 차단 | 미반영 시 미완성 거래 폼이 실수로 제출되는 회귀 위험이 가장 크다 |
| P0 | `ArrowUp`/`ArrowDown`으로 강조 인덱스 이동 + `aria-activedescendant` 연동 | 이번 검토가 요청받은 핵심 기능 |
| P1 | `role="combobox"`/`listbox`/`option` 최소 ARIA 골격 추가 | 스크린 리더 사용자가 현재 상태(펼침/강조 항목)를 알 수 있어야 함 |
| P1 | 마우스 클릭 시 버튼이 DOM 포커스를 가져가지 않도록 `onMouseDown` 방지 | 가상 포커스(`aria-activedescendant`)와 실제 포커스가 어긋나는 것을 방지 |
| P1 | 검색 결과 갱신 시 강조 인덱스 초기화 | 이전 검색의 강조 위치가 새 결과에 잘못 매핑되는 것을 방지 |
| P2 | 키보드 강조에 대응하는 새 시각적 강조 스타일(`App.css`) | 버튼이 더 이상 실제 포커스를 받지 않으므로 기존 `:focus-visible` 스타일이 무력화됨 |
| P2 | 순환 탐색, 타이프어헤드, 바깥 클릭 닫기, `Home`/`End` | 4번 질문에서 제외 근거를 설명한 고급 패턴 |

## 구현 시 주의할 회귀 위험

- **폼 오제출**: `Enter` 처리를 추가할 때 강조 항목이 없는 분기에서도
  실수로 `preventDefault`를 호출하면, 사용자가 폼을 다 채운 뒤 `Enter`로
  제출하던 기존 흐름이 깨진다. 강조 항목이 있을 때만 막아야 한다.
- **강조 인덱스 stale 참조**: `market`이나 `ticker`가 바뀌어 새 검색이
  시작되면(`searchKey` 변경) 이전 강조 인덱스가 새 `results` 배열 범위를
  벗어나거나 다른 종목을 가리킬 수 있다. `searchKey`가 바뀔 때마다 강조
  인덱스를 초기화해야 한다.
- **`Escape` 동작 유지**: 새 키보드 핸들러를 추가하면서 기존
  `if (event.key === "Escape") setIsSearchOpen(false);` 로직을 같은
  핸들러 안에 통합해야 한다. 별도 핸들러로 분리하면 이벤트 순서에 따라
  하나가 다른 하나를 덮어쓸 수 있다.
- **스크롤/캐럿 이동 부작용**: `ArrowUp`/`ArrowDown`에서 `preventDefault`를
  빠뜨리면 입력 필드 안에서 캐럿이 이동하거나(입력에 값이 있을 때) 페이지가
  스크롤될 수 있다.
- **시각적 강조 누락**: ARIA만 추가하고 `App.css`에 강조 스타일을 추가하지
  않으면, 스크린 리더에는 강조 항목이 전달되지만 눈으로 보는 키보드
  사용자(저시력·모터 장애 등 스크린 리더를 쓰지 않는 사용자)는 어떤 항목이
  선택되려 하는지 알 수 없다.
- **`aria-controls`/`aria-activedescendant`의 댕글링 참조**: 결과가 0건이
  되는 순간(오류·빈 결과 전환) `resultsId`를 가진 `<ul>`이 DOM에서
  사라지므로, 그 시점에 `aria-controls`뿐 아니라 `aria-activedescendant`도
  함께 비워야 한다. 현재 `aria-controls` 조건은 이미 처리되어 있으니 같은
  조건을 `aria-activedescendant`에도 적용하면 된다.

## 수동 검증 시나리오

1. 유효한 티커 접두어를 입력해 결과가 여러 건 뜨면 `ArrowDown`을 반복 →
   강조가 첫 항목부터 마지막 항목까지 순서대로 이동하고, 마지막에서 멈춘다.
   이어서 `ArrowUp`을 반복 → 첫 항목에서 멈춘다.
2. 항목을 강조한 상태에서 `Enter` → 입력값이 선택한 티커로 바뀌고 드롭다운이
   닫히며, **폼이 제출되지 않는다**(성공 메시지나 페이지 이동이 발생하지
   않아야 한다).
3. 드롭다운이 닫혀 있거나 강조 항목이 없는 상태에서 필수 필드를 채운 뒤
   `Enter` → 기존과 동일하게 폼이 정상 제출된다(회귀 여부 확인).
4. 결과 없음 또는 오류 메시지가 보이는 상태에서 `ArrowDown`/`ArrowUp`/`Enter`
   → 화면에 아무 변화가 없고 콘솔 에러도 없다. `Escape`는 여전히 피드백
   영역을 닫는다.
5. 결과가 강조된 상태에서 `Escape` → 드롭다운이 닫히고, 입력 텍스트는
   그대로 남으며, 포커스는 입력 필드에 유지된다. 다시 입력하면 강조 없이
   새 검색이 시작된다.
6. 드롭다운이 열려 강조된 상태에서 시장을 바꾸거나 입력값을 수정해 새 검색이
   시작되면 → 이전 강조가 초기화되고, 브라우저 개발자 도구에서
   `aria-activedescendant`가 존재하지 않는 `id`를 가리키지 않는지 확인한다.
7. VoiceOver 또는 NVDA로 입력에 포커스를 두고 검색 → "콤보박스, 펼쳐짐" 류의
   역할·상태가 안내되고, `ArrowDown`/`ArrowUp` 이동 시 각 항목의 티커/종목명이
   안내되는지 확인한다.
8. 키보드로 몇 차례 탐색한 뒤 마우스로 다른 항목을 클릭 → 정상적으로 그
   항목이 선택되고, 클릭 직후에도 포커스가 입력 필드에 남아 있는지 확인한다
   (버튼이 포커스를 가로채 `Tab` 순서가 흐트러지지 않아야 한다).

## 검증 방법

정적 코드 검토로 수행했다. `AssetSearchInput.tsx`, `App.css`,
`TradeTransactionEntryPage.tsx`, `docs/design/ASSET_SEARCH_FEEDBACK_REVIEW.md`를
교차 확인했으며, 프런트엔드 빌드나 브라우저 수동 실행은 하지 않았다(이 작업
계약은 `docs/design/ASSET_SEARCH_KEYBOARD_REVIEW.md` 외 파일 쓰기를 허용하지
않으며, `frontend/`는 읽기 전용 컨텍스트로만 사용했다).
