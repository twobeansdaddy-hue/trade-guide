# Agent Task Contract

## Identity

- Task ID: `antigravity-accordion-title-spacing-fix-20260916`
- Owner: `Antigravity CLI`
- Work mode: `scoped-implementation`
- Branch / worktree: 현재 브랜치(main)에서 진행, 별도 브랜치 불필요

## Outcome

사용자가 스크린샷으로 지적한 두 가지 레이아웃 문제를 고친다.

1. 아코디언 제목 줄에서 제목 텍스트와 그 옆 배지가 간격 없이 붙어 있다(예: "매매
   계획 초안"과 "검토 전용 · 자동 주문 없음" 배지 사이 간격 없음).
2. 종목명 + 시장 배지(`US`) + 티커를 한 줄에 나란히 배치한 카드에서, 종목명이 길어
   줄바꿈되면(예: "Direxion Daily Semiconductor Bull 3X Shares") 시장 배지와 티커만
   다음 줄에 따로 남아 종목명과 시각적으로 분리되어 어색해 보인다. 사용자는 "US
   태그를 앞에 붙이거나, 최소한 이렇게 사이에 애매하게 끼는 건 이상하다"고 지적했다.

## Background (읽기 전용 참고, 진단 완료)

### 문제 1: 제목-배지 간격

- `frontend/src/components/strategy/TradePlanPreviewSection.tsx:64-69`와
  `frontend/src/pages/StrategyGuidesPage.tsx:180` 두 곳에서 아코디언 `title` prop에
  `<span className="section-title-row"><span>제목</span><span
  className="...badge">...</span></span>` 형태로 제목과 배지를 함께 전달한다.
- `frontend/src/App.css`에는 **`.section-title-row`에 대한 CSS 규칙이 전혀 없다**
  (검색 결과 0건). 기본값(`inline`)으로 렌더링되어 안쪽 두 `<span>`이 붙어버린다.
- 부모인 `.accordion-title-text`(App.css:11052)는 `display: inline-flex; gap: 8px`를
  갖고 있지만, `.section-title-row`가 그 아래의 **단일 자식**으로 들어가기 때문에
  이 gap이 적용되지 않는다.

### 문제 2: 종목명·시장 배지·티커 줄바꿈

- 이전 작업(`antigravity-ticker-display-name-and-alignment-fix-20260916.md`)에서
  `TradePlanPreviewSection.tsx`, `StrategyGuideList.tsx`, `PremarketGuidePanel.tsx`
  세 곳에 종목 한글명을 추가할 때, 종목명·시장 배지·티커를 **한 줄에 나란히 나열하는
  평면 구조**로 구현했다(예: `<strong>{displayName}</strong><span
  className="market-badge">{market}</span><span
  className="broker-ticker">{ticker}</span>`가 모두 형제 요소로 flex-wrap 컨테이너
  안에 있음). 종목명이 짧으면 문제없지만 길면(SOXL의 정식명 등) 줄바꿈되면서 시장
  배지+티커만 다음 줄에 남아 붕 뜬 것처럼 보인다.
- 반면 원래 검증된 참고 패턴(`frontend/src/components/broker/BrokerHoldingSnapshotSection.tsx:639-644`)은
  종목명을 **별도 줄(첫 줄)**에 두고, 시장 배지와 티커를 **하나의 하위 그룹
  (`.broker-snapshot-item-symbol`)으로 묶어 그 아래 줄**에 배치한다:
  ```tsx
  <div className="broker-snapshot-item-identity">
      <strong className="broker-snapshot-item-name">{item.displayName}</strong>
      <div className="broker-snapshot-item-symbol">
          <span className="market-badge">{item.market}</span>
          <span className="broker-ticker">{item.ticker}</span>
      </div>
  </div>
  ```
  이 구조는 이름이 아무리 길어도 시장 배지+티커가 항상 붙어서 한 그룹으로 움직이므로
  어색하게 분리되지 않는다. `.broker-snapshot-item-identity`(App.css:1140)와
  `.broker-snapshot-item-symbol`(App.css:1160)에 이미 필요한 스타일이 정의되어 있다.
- 세 파일의 관련 위치:
  - `frontend/src/components/strategy/TradePlanPreviewSection.tsx` (약 210~230줄,
    `trade-plan-asset` 내부)
  - `frontend/src/components/strategy/StrategyGuideList.tsx` (`guide-asset` 내부)
  - `frontend/src/components/strategy/PremarketGuidePanel.tsx` (`premarket-guide-row-name` 내부)

## Allowed Files

- `frontend/src/App.css`
- `frontend/src/components/strategy/TradePlanPreviewSection.tsx`
- `frontend/src/components/strategy/StrategyGuideList.tsx`
- `frontend/src/components/strategy/PremarketGuidePanel.tsx`

## Non-Goals And Guardrails

- displayName이 없거나 ticker와 같을 때의 기존 폴백 표시(시장 배지+티커만 표시)는
  그대로 유지한다.
- 이미 정상 동작하는 `.broker-snapshot-item-identity`/`.broker-snapshot-item-symbol`
  CSS 자체는 변경하지 않는다(재사용만 한다). 필요하면 `.trade-plan-asset`,
  `.guide-asset`, `.premarket-guide-row-name` 쪽에서 이 하위 그룹이 자연스럽게
  배치되도록 컨테이너 스타일만 조정한다.
- `.section-title-row`를 고칠 때 `.accordion-title-text`의 기존 gap 동작(다른
  아코디언 제목에는 배지가 없는 경우도 있음)을 깨지 않는다.

## Acceptance Checks

- [x] `.section-title-row`에 `display: inline-flex; align-items: center; gap: 8px`
      (또는 동등한 간격 규칙)을 추가해, 제목 텍스트와 배지 사이에 명확한 간격이 생긴다.
- [x] `TradePlanPreviewSection.tsx`, `StrategyGuideList.tsx`, `PremarketGuidePanel.tsx`
      세 곳 모두에서 시장 배지+티커가 종목명과 별개로, 항상 서로 붙은 하나의 그룹으로
      표시된다(참고 패턴과 동일 구조).
- [x] SOXL처럼 긴 영문 정식명이 줄바꿈될 때, 시장 배지+티커 그룹이 어색하게 분리되지
      않고 이름 아래 자연스러운 둘째 줄로 배치된다.
- [x] displayName이 ticker와 같은 경우(등록된 한글명 없음)의 기존 표시는 그대로다.
- [x] `npm run lint`, `npm run build` 통과.
- [x] 데스크톱과 375px 폭에서 "매매 계획 초안"(PFE, SOXL 포함), "보유 종목 가이드",
      "장전 가이드 요약" 세 화면을 브라우저로 확인해 두 문제 모두 해결됐는지 검증한다.

## Handoff

- Files changed:
  - `frontend/src/App.css`
  - `frontend/src/components/strategy/TradePlanPreviewSection.tsx`
  - `frontend/src/components/strategy/StrategyGuideList.tsx`
  - `frontend/src/components/strategy/PremarketGuidePanel.tsx`
  - `docs/agent-tasks/antigravity-accordion-title-spacing-fix-20260916.md`
- Verification run:
  - `npm run lint`: pass (ESLint cleanly passed)
  - `npm run build`: pass (tsc + vite build cleanly passed)
  - Headless Chrome 자동화 검증 (1280px 데스크톱 & 375px 모바일):
    - `.section-title-row` title과 badge 간격 8px 실측 확인 (매매 계획 초안: 8px, Track A 후보 가이드: 8px)
    - 긴 종목명(SOXL: "Direxion Daily Semiconductor Bull 3X Shares") 줄바꿈 시 시장 배지(`US`)와 티커(`SOXL`)가 `.broker-snapshot-item-symbol` 그룹으로 묶여 종목명 아래 2번째 줄에 안정적으로 유지됨을 확인
    - `displayName === ticker`(JEPQ, TQQQ 등) 시 기존 단일 라인 폴백 정상 유지 확인
    - 375px 폭에서 가로 스크롤/넘침 없음 확인 (`scrollWidth === clientWidth === 375`, overflow 0건)
- API / data-model / policy impact: 없음 (프론트엔드 스타일 및 마크업 구조 개선 전용)
- Open decision or risk: 없음
