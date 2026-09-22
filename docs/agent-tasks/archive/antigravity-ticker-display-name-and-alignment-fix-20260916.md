# Agent Task Contract

## Identity

- Task ID: `antigravity-ticker-display-name-and-alignment-fix-20260916`
- Owner: `Antigravity CLI`
- Work mode: `scoped-implementation`
- Branch / worktree: 현재 브랜치(main)에서 진행, 별도 브랜치 불필요

## Outcome

두 가지를 처리한다.

1. 전략 가이드·매매 계획 초안·장전 가이드 화면에서 티커만 보이던 곳에 종목 한글명(또는
   등록된 표시명)을 함께 표기해 인식성을 높인다. 백엔드는 이미 `displayName` 필드를
   추가·검증 완료했다.
2. `PremarketGuidePanel.tsx`의 요약 행(`.premarket-guide-row`)에서 상태 배지 폭이
   행마다 달라 사유 텍스트 시작 위치가 어긋나는 정렬 버그를 고정한다.

## Background (읽기 전용 참고)

### 1) displayName 필드 추가 (백엔드 완료)

다음 API 응답에 `displayName` 필드가 추가됐다(각 항목의 `market`/`ticker`와 같은
레벨). `AssetListing`에 등록된 표시명이며, 등록되지 않은 티커는 티커 문자열 그대로
내려온다(예: 위 검증에서 `JEPQ`, `TQQQ`는 아직 `AssetListing`에 한글명이 없어 티커
그대로 응답됨 — 정상 동작이며 프론트에서 별도 처리 불필요).

- `GET .../strategy-guides`, `GET .../candidate-strategy-guides`
  → `guides[].displayName`
- `GET .../trade-plan-preview`
  → `candidatePlans[].displayName`, `heldAssetPlans[].displayName`
- `GET/POST .../premarket-guide/today`
  → `heldGuides[].displayName`, `candidateGuides[].displayName`

실제 응답 예시(검증 완료):
```json
{"ticker": "PFE", "displayName": "화이자", ...}
{"ticker": "SOXL", "displayName": "Direxion Daily Semiconductor Bull 3X Shares", ...}
{"ticker": "JEPQ", "displayName": "JEPQ", ...}
```

이미 `frontend/src/types/strategyGuide.ts`, `frontend/src/types/tradePlanPreview.ts`,
`frontend/src/types/premarketGuide.ts`에 `displayName?: string` 또는 `displayName:
string` 필드를 추가해야 타입이 API와 일치한다(현재 타입에는 없음, 백엔드만 추가된
상태).

### 2) 정렬 버그 원인 (진단 완료)

`frontend/src/App.css:891-893`:
```css
.premarket-guide-row {
    display: grid;
    grid-template-columns: 44px minmax(72px, 0.35fr) auto minmax(0, 1fr);
    ...
}
```
각 `.premarket-guide-row`가 **독립된 grid 컨테이너**라서 3번째 칼럼(상태 배지, `auto`
폭)이 행마다 배지 텍스트 길이("보유" vs "매수 검토" vs "비중 축소")에 따라 다르게
계산되고, 그 결과 4번째 칼럼(사유 텍스트)의 시작 x좌표가 행마다 어긋난다.

가능한 배지 라벨(`PremarketGuidePanel.tsx`의 `toActionLabel`): 매수 검토, 보유, 비중
축소, 매도 검토, 관찰.

## Allowed Files

- `frontend/src/types/strategyGuide.ts`
- `frontend/src/types/tradePlanPreview.ts`
- `frontend/src/types/premarketGuide.ts`
- `frontend/src/components/strategy/StrategyGuideList.tsx`
- `frontend/src/components/strategy/TradePlanPreviewSection.tsx`
- `frontend/src/components/strategy/PremarketGuidePanel.tsx`
- `frontend/src/App.css` (이 화면들에 필요한 스타일만)

## Non-Goals And Guardrails

- API 계약·백엔드 로직은 변경하지 않는다(이미 완료됨, 읽기 전용).
- `displayName`이 티커와 동일하게 내려오는 경우(등록된 표시명이 없을 때) 별도 배지나
  경고를 붙이지 않는다. 그냥 자연스럽게 티커만 보이는 것으로 충분하다.
- 표시 형식은 "종목명 · 티커" 또는 "종목명 (티커)"처럼 한글명이 먼저, 영문 티커를
  보조로 두는 기존 화면(예: `BrokerHoldingSnapshotSection.tsx`의
  `broker-snapshot-item-name`/`broker-ticker` 패턴)과 일관되게 맞춘다. 새로운 표기
  규칙을 임의로 만들지 않는다.
- 정렬 수정은 `.premarket-guide-row` 그리드 구조를 유지하면서 3번째 칼럼 폭만 모든
  배지 라벨이 잘리지 않는 고정 폭(또는 `minmax(고정최소, auto)`)으로 바꾸는 최소
  변경으로 처리한다. 전체 레이아웃을 새로 설계하지 않는다.

## Acceptance Checks

- [ ] 전략 가이드 목록(보유·후보), 매매 계획 초안 카드, 장전 가이드 요약 행에서 티커와
      함께 `displayName`이 표시된다.
- [ ] `displayName === ticker`인 항목(등록된 한글명 없음)에서 어색한 중복 표기("SOXL
      (SOXL)" 등)가 생기지 않는다.
- [ ] `.premarket-guide-row`에서 "보유"(2글자)와 "매수 검토"/"비중 축소"(4글자) 배지가
      섞여도 그 뒤 사유 텍스트 시작 위치가 행마다 동일하다(스크린샷 또는 좌표 비교로
      확인).
- [ ] `npm run lint`, `npm run build` 통과.
- [ ] 데스크톱과 375px 폭에서 가로 스크롤·잘림·겹침 없음.
- [ ] 실제 브라우저에서 최소 2개 화면(전략 가이드, 장전 가이드)을 열어 한글명 노출과
      정렬 수정을 시각적으로 확인.

## Handoff

- Files changed:
- Verification run:
- API / data-model / policy impact: 없음(백엔드는 이미 반영·검증됨)
- Open decision or risk:
