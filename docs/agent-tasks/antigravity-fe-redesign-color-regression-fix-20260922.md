# Agent Task Contract

## Identity

- Task ID: `antigravity-fe-redesign-color-regression-fix-20260922`
- Owner: `Antigravity CLI` → 실제로는 Claude가 수행함(사용자가 코디네이터 스코프를 넓혀 위임, 2026-09-22)
- Work mode: `scoped-implementation`
- Branch / worktree: current `main` working tree (uncommitted FE redesign changes already present)

## Outcome

1라운드 FE 리디자인 작업(`frontend/`) 결과가 원본 Claude Design 시안과 크게 달라 보인다는 사용자 피드백이 있었다. 원인은 `App.css`에 남아 있는 구(舊) 스타일 전체에 텍스트 색상이 `var(--border)`로 잘못 치환되어 있어 배경과 거의 구분 안 되는 상태이기 때문이다. 이 작업은 그 색상 회귀를 고치고, 동봉된 시안 원본과 시각적으로 정합시키는 것이 목표다.

## Design Source (Ground Truth)

로컬에 시안 번들이 있다: `/Users/beansdaaddy/Downloads/untitled/project/`
- `Trade Guide 리디자인.dc.html` — 1차 참조 대상(6개 화면 전체의 리터럴 인라인 스타일 포함, 라이트/다크 색상표 포함)
- `doc-page.js`, `support.js` — 위 파일이 import하는 런타임
- `Trade Guide 현재 화면.dc.html`, `디자인 리뷰.dc.html` — 이전/진단 참고용
- `frontend/src/App.css`, `frontend/src/index.css` — 시안 제작 당시 참조했던 실제 코드 스냅샷(비교용)

README(`untitled/README.md`)가 명시한 대로 `Trade Guide 리디자인.dc.html`을 처음부터 끝까지 읽고, 그 안의 인라인 스타일 값(px, 색상 변수, 폰트)을 리터럴 그대로 가져와 재현할 것. 프로토타입의 내부 마크업 구조를 그대로 베낄 필요는 없고, 시각 결과만 일치시키면 된다.

## Root Cause (확인됨)

`frontend/src/App.css`에서 텍스트 색상(`color:`) 선언 **566곳**이 `var(--border)`로 되어 있다. `--border`는 테두리용 연한 색(라이트 `#e8ecef`, 다크 `#2a2a2f`)이라 배경(`--bg`/`--surface`)과 거의 구분되지 않는다. 이게 브랜드 로고, 탭, 페이지 제목(`h1`)·설명문, 카드 수치, 배지, 배너 텍스트 등 거의 전 화면에 걸쳐 나타난다. 사용자가 "매매 기록 화면에 빈 공백이 크게 뜬다"고 본 것도 실제로는 비어있는 게 아니라 `.page-header h1`/`.page-header p`(App.css:626, 534) 텍스트가 배경색과 거의 같은 색이라 안 보이는 것이었다. `.page-header`는 6개 페이지 전부(`DashboardPage`, `HoldingsPage`, `TransactionsPage`, `StrategyGuidesPage`, `BrokerAccountsPage`, `SettingsPage`)에서 공통으로 쓰인다.

추가로 확인된 것:
- `App.css:745` 부근 `.summary-grid strong { color: var(--border); font-size: 25px; }`가 새로 만든 `frontend/src/styles/pages/dashboard.css`의 `.summary-value`(특정성 낮음)를 특정성으로 이겨서 대시보드 카드 숫자가 25px 회색으로 렌더링됨. 이미 `dashboard.css`로 이전된 규칙이므로 App.css 쪽은 삭제해야 함(4524, 4925, 4941, 4997번째 줄 부근의 관련 미디어쿼리도 함께).
- `App.css:601`과 `App.css:10040`에 `.page-content`가 중복 정의됨.
- `.text-positive`/`.text-warning`/`.text-accent`(App.css:1013, 1028, 1043) 유틸리티 클래스가 전부 `var(--muted)`로 잘못 통일되어 있음 — 각각 `var(--gain)`, `var(--warn)`, `var(--strong)`(또는 `--ink`)여야 함.
- `.exposure-heading > strong.negative`(App.css:4189)가 `var(--border)`로 돼 있음 — 한도 초과 종목 표시이므로 `var(--loss)`여야 함.
- `frontend/src/styles/layout.css`의 `.theme-toggle`, `.theme-toggle button`, `.account-button`, `.account-name`에 `flex-shrink: 0`/`white-space: nowrap`이 없어서 좁은 화면에서 "Dark"가 "Dar"로 잘리고 계정 닉네임이 두 줄로 줄바꿈됨. 시안(`renderVals()`의 `themeBase` 문자열)에는 `white-space:nowrap`이 이미 포함되어 있으니 그대로 반영할 것.

## Allowed Files

- `frontend/src/App.css`
- `frontend/src/styles/**`
- `frontend/src/components/layout/AppLayout.tsx` (className 구조 조정이 꼭 필요한 경우에 한해)
- `frontend/src/pages/*.tsx`, `frontend/src/components/**/*.tsx` (className만 조정, 데이터 바인딩·API 호출·상태 로직은 건드리지 않음)

## Non-Goals And Guardrails

- API 계약, 라우팅 경로, 데이터 페칭 로직은 변경하지 않는다.
- 브랜드명("Trade Guide")은 시안의 "Ledger & Signal"로 바꾸지 않는다 — 시안은 톤 참고용이며 실제 제품명은 기존 그대로 유지한다(확실치 않으면 사용자에게 먼저 확인).
- 색상은 반드시 `tokens.css`의 CSS 변수만 사용한다. 새 hex 값을 넣지 않는다.
- `App.css`를 통째로 재작성하지 말고, 위에서 지목한 잘못된 선언들을 교정 + 이미 `styles/pages/*.css`로 이전된 블록은 삭제하는 방식으로 정리한다. 아직 이전되지 않은 라이브 스타일(브로커/전략 가이드 아코디언 등 심층 패널)까지 무리하게 새로 재작성하지 말고, 최소한 `color: var(--border)` 오염만 전부 제거해 가독성을 회복시킨다.

## Acceptance Checks

- [ ] `rg "color: var\(--border\)" frontend/src/App.css` 결과가 비어 있다.
- [ ] 6개 화면 × 라이트/다크 = 12개 조합을 스크린샷으로 확인, 배경에 묻히는 텍스트가 없다.
- [ ] 대시보드 요약 카드 숫자가 `clamp(28px,3.4vw,38px)` 크기로 렌더링된다(25px로 축소되어 있지 않다).
- [ ] 상단바에서 "Light"/"Dark" 버튼 텍스트가 잘리지 않고, 계정 닉네임이 한 줄로 표시된다.
- [ ] `.page-content` 중복 정의가 제거되어 하나만 남는다.
- [ ] `rg '!important' frontend/src` 결과가 비어 있다(기존에 통과된 상태 유지).
- [ ] `rg '#[0-9a-fA-F]{6}' frontend/src --glob '!**/tokens.css'` 결과가 비어 있다(기존에 통과된 상태 유지).
- [ ] `npm run build` 통과.
- [ ] 기존 테스트 통과.

## Handoff

- Files changed: `frontend/src/App.css`, `frontend/src/pages/BrokerAccountsPage.tsx`.
  - 원래 계약이 지목한 `color: var(--border)` 566곳 회귀는 이미 (다른 세션에서)
    수정되어 있었음 — 확인만 하고 손대지 않음.
  - 대신 사용자가 재현한 "엉망" 현상의 실제 원인은 **`background: var(--border)`**
    오염(566곳 회귀와 같은 성격의 별도 사고)이었음. 그중 같은 CSS 규칙 블록 안에
    `border: 1px solid var(--border)`가 함께 있어 "배경색 = 테두리색"이 되어
    테두리가 안 보이고 패널이 배경과 구분 안 되던 **95곳**을 `background:
    var(--surface)`로 교정(정규식 기반 블록 매칭 스크립트, 수동 확인 후 적용).
  - 나머지 약 170곳의 `background: var(--border)`(hover 상태, 작은 뱃지/필
    등)는 손대지 않음 — 정상적인 용도로 보이며, 전수 검증하지 않았으므로 추가
    확인이 필요할 수 있음.
- Verification run:
  - `npm run build` 통과 (CSS 문법 오류 없음)
  - `npm run lint` — 기존에 존재하던 6개 에러/1개 경고(모두 `.tsx` 파일, 이번
    CSS 변경과 무관)는 그대로 남아 있음, 새로 추가된 lint 에러 없음
  - 브라우저 수동 확인: `/broker-accounts`(연동 계좌) 라이트/다크 모드에서
    "안전 가이드" 배너, 원장 반영 정합성 카드가 배경과 구분되는 카드로
    렌더링됨을 확인. 6개 화면 × 라이트/다크 전체 조합(원래 Acceptance
    Checks 항목)까지는 확인하지 못함.
  - `rg "color: var\(--border\)" frontend/src/App.css` — 비어 있음(통과)
  - `.page-content` 중복, `text-positive/warning/accent`, `exposure-heading
    negative`, `!important`, hex 색상 항목 — 전부 이미 정상 상태 확인
  - 헤더 "Light"/"Dark" 잘림, 계정 닉네임 줄바꿈 항목은 이번에 별도 확인하지
    않음
- 추가 라운드(사용자가 시안과 "차이가 큽니다"라고 재지적한 뒤 진행):
  `/Users/beansdaaddy/Downloads/Trade Guide 리디자인.dc.html`(사용자가 제공한
  실제 원본 경로 — 최초 작업 계약에 적힌 `/Users/beansdaaddy/Downloads/untitled/project/`
  경로와 다름, 최신본은 전자)을 전체 읽고 대시보드·보유종목·매매기록·전략가이드
  ·연동계좌·설정 6개 화면을 코드와 줄 단위로 대조했다.
  - 연동 계좌 헤더에 시안엔 있는 "STEP n / 5" 표시가 누락된 것을 발견해 추가함
    (`.page-header-top`/`.page-header-meta` 신규 CSS + `BrokerAccountsPage.tsx`
    JSX 수정, 기존 `activeStep` 값 재사용, 새 상태·API 없음).
  - `styles/pages/*.css`(dashboard/holdings/transactions/strategy-guides/broker)
    전수 grep 검사 — `background: var(--border)` 오염 없음, 이미 깨끗함. 버그는
    legacy `App.css`에만 있었고 위에서 이미 수정됨.
  - 6개 화면 라이트/다크 전부 브라우저로 직접 열람 확인 — 색상 대비, 카드
    구분, 리스트 레이아웃 전부 정상.
  - 시안과 다르지만 버그가 아닌 것(의도적 변경으로 판단해 유지, 사용자 승인
    없이 되돌리지 않음): 대시보드/보유종목 헤더의 우측 요소가 시안은 메타
    텍스트인데 현재는 실제 동작하는 CTA 버튼("전략 가이드 보기" 등); 설정
    화면이 시안의 평면 구조 대신 아코디언 구조; 전략가이드 "TRACK A" 라벨
    위치가 h1과 같은 줄이 아니라 위 줄.
- API / data-model / policy impact: 없음(프런트엔드 전용, CSS/JSX만 변경)
- Open decision or risk:
  - 브랜드명("Ledger & Signal" vs "Trade Guide") 확인 필요 여부 — 미해결
  - 나머지 170곳의 `background: var(--border)`(hover 상태, 작은 뱃지 등으로
    보임) 전수 검토는 하지 않음
  - 위에 나열한 "의도적으로 보이는" 3가지 차이를 시안과 완전히 일치시킬지는
    사용자 결정 필요
