# 검토용 매매 계획 UI 작업 계약

## Identity

- Task ID: `antigravity-trade-plan-preview-ui-20260915`
- Owner: `Antigravity`
- Work mode: `antigravity-ui`
- Branch / worktree: current

## Outcome

신규 read-only API `GET /api/members/{memberId}/portfolios/{portfolioId}/trade-plan-preview`를 전략 가이드 화면에 검토용 `매매 계획 초안`으로 표시한다. 자동 주문, 저장, 증권사 호출을 만들지 않는다.

## Allowed Files

- `frontend/src/**`
- `docs/agent-tasks/antigravity-trade-plan-preview-ui-20260915.md`

## Required UI

- `StrategyGuidesPage`에서 장전 가이드 다음, 보유 종목 가이드 이전에 compact section으로 배치한다.
- API/type/component을 새로 추가하거나 기존 frontend 파일을 최소 수정한다.
- 매수 `BUY`: 기준가, 사용자 손절가, 최대 검토 금액, 추정 수량, 예상 최대 손실을 표기한다. `AVAILABLE_CASH_NOT_SYNCED`는 `가용 현금 미연동`으로 명확하게 표기한다.
- 보유 `STOP_LOSS_EXIT_REVIEW`: 현재 보유 수량 전량의 손절 검토임을 명확히 하고 주문/실행 버튼은 만들지 않는다.
- `SELL_REVIEW`, `HOLD`, `WATCH`, `NOT_READY`에는 수량이나 금액을 임의 표시하지 않는다. 정상적인 HOLD/WATCH는 오류 색을 쓰지 않는다.
- API가 빈 결과여도 조용한 빈 상태를 제공한다. `unavailableAssets`의 `ASSET_PROFILE_NOT_FOUND`는 빨간 오류가 아니라 `전략 프로필 설정 필요` 안내로 표시한다.
- 기준가는 실시간 주문가가 아니라 전략 기준 가격이며, 모든 계획은 최종 사용자 확인이 필요하다는 짧은 안내를 보여 준다.
- 정책/손절가 미설정 NOT_READY에는 해당 설정 영역으로 이동할 수 있는 내부 앵커 링크를 제공한다.

## Visual Gate

- 기존 디자인 토큰과 버튼/입력 높이를 따른다. 카드 안에 또 카드 형태를 만들지 않는다.
- 데스크톱(1280px)과 모바일(360px)에서 가로 overflow, 잘린 텍스트, 카드 외부로 나간 버튼, 줄바꿈으로 인한 불규칙한 영역 높이가 없어야 한다.
- 숫자는 고정 폭 글꼴을 새로 도입하지 말고 기존 포맷 유틸과 스타일을 우선 사용한다.
- 사용자 데이터 또는 API 응답을 임의로 바꾸는 목업/하드코딩을 금지한다.

## Boundaries

- 백엔드, DB, Gradle, 환경 변수, 인증, 브로커 API, 의존성, Git에는 손대지 않는다.
- 파일 외부 수정, 커밋, 푸시 금지.
- 브라우저 접근이 불가하면 코드·lint·build까지만 수행하고, 시각 최종 검증은 Codex에 넘긴다.

## Handoff

- 변경 파일:
  - `frontend/src/types/tradePlanPreview.ts`: 매매 계획 미리보기 배치/항목 TypeScript 타입 정의
  - `frontend/src/api/tradePlanPreviewApi.ts`: `GET /api/members/{memberId}/portfolios/{portfolioId}/trade-plan-preview` API 클라이언트
  - `frontend/src/components/strategy/TradePlanPreviewSection.tsx`: 검토용 매매 계획 초안 컴포넌트 구현
  - `frontend/src/pages/StrategyGuidesPage.tsx`: 장전 가이드와 보유 종목 가이드 사이에 compact section 배치 및 갱신 연동
  - `frontend/src/pages/SettingsPage.tsx`: `#risk-policy` 내부 앵커 해시 스크롤 및 form id 지원
  - `frontend/src/App.css`: `.trade-plan-*` 및 900px, 480px, 360px 반응형 스타일과 `.action-badge.not-ready` 중립 스타일
- lint/build 결과:
  - `npm run lint` 통과 (0 errors, 0 warnings)
  - `npm run build` 통과 (`tsc -b && vite build` 성공)
- UI 상태별 처리:
  - `BUY`: 기준가, 사용자 손절가, 최대 검토 금액, 추정 수량, 예상 최대 손실 표시. `AVAILABLE_CASH_NOT_SYNCED`는 `가용 현금 미연동` 태그 및 계좌 직접 확인 안내로 표기.
  - `STOP_LOSS_EXIT_REVIEW`: 보유 수량 전량 손절 검토임을 명확한 경고 콜아웃으로 안내하며 주문/실행 버튼 미제공.
  - `SELL_REVIEW`: 매도 검토 상태와 기준가만 표시하며 수량·금액 미표기.
  - `HOLD`: 중립 파랑 배지 및 기준가/손절가 표시, 수량·금액 미표기.
  - `WATCH`: 중립 황갈색 배지 및 기준가 표시, 수량·금액 미표기.
  - `NOT_READY`: 중립 회색 배지 및 `MISSING_RISK_POLICY` / `MISSING_STOP_LOSS`에 맞춰 `/settings#risk-policy`로 이동하는 내부 앵커 링크 제공, 수량·금액 미표기.
  - 빈 결과/제외 종목: 조용한 빈 상태 제공. `ASSET_PROFILE_NOT_FOUND`는 빨간 오류가 아닌 `전략 프로필 설정 필요` 안내 및 `#held-asset-strategy-profiles` 스크롤 앵커 링크 제공.
  - 기준가 안내: 실시간 호가가 아닌 전략 기준 가격이며 자동 주문이 아니라는 점을 상단 배너로 명시.
- 남은 시각 검증 위험:
  - 브라우저 headless 실행 환경 특성상 실제 1280px/360px 브라우저 렌더링 화면의 시각적 여백 및 줄바꿈 균형은 Codex의 최종 수동 검수 권장.

