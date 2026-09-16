# 검토용 매매 계획 API 작업 계약

## Identity

- Task ID: `claude-trade-plan-preview-api-20260915`
- Owner: `Claude`
- Work mode: `claude-backend`
- Branch / worktree: `feature/candidate-entry-window`

## Outcome

Track A 전략 가이드에 대해 주문을 전송하지 않는 검토용 매매 계획 API를 만든다. 매수 후보는 사용자 설정 손절가와 포트폴리오 위험 한도로 최대 검토 금액·추정 수량·예상 최대 손실을 계산하고, 보유 종목은 손절 기준 가격 도달 여부를 별도 표시한다.

## Allowed Files

- `src/main/java/com/tradeguide/domain/strategy/**`
- `src/main/java/com/tradeguide/dto/strategy/**`
- `src/main/java/com/tradeguide/service/strategy/**`
- `src/main/java/com/tradeguide/controller/portfolio/PortfolioController.java`
- `src/test/java/com/tradeguide/domain/strategy/**`
- `src/test/java/com/tradeguide/service/strategy/**`
- `src/test/java/com/tradeguide/controller/portfolio/PortfolioControllerTest.java`
- `src/test/java/com/tradeguide/controller/portfolio/PortfolioControllerMemberBoundaryTest.java`

## Approved Policy And API Contract

- Add a read-only endpoint: `GET /api/members/{memberId}/portfolios/{portfolioId}/trade-plan-preview`.
- Do not persist plans, create transactions, call broker order APIs, add a database migration, or change authentication.
- Use existing portfolio strategy guides, candidate strategy guides, risk policy, holdings, and valuation services. Do not invent a new technical indicator or modify Track A entry/exit rules.
- A candidate `BUY` plan can be calculated only when a user-configured stop-loss price exists and the risk policy is configured.
- Use the strategy reference price as the entry reference price. It is not a live quote, order price, or execution promise.
- For a candidate BUY: calculate risk-limited quantity from `portfolio market value * maxLossPerTradeRatio / (entry reference price - stop-loss price)`. Cap the result by remaining single-asset exposure. Return amount, quantity, estimated maximum loss, and constraints.
- The broker cash balance is not yet a reliable cross-provider contract. Return an explicit `AVAILABLE_CASH_NOT_SYNCED` constraint; do not fabricate cash or claim the amount is affordable.
- For held assets: if the guide reference price is at or below the configured stop-loss price, return a `STOP_LOSS_EXIT_REVIEW` plan for the entire currently held quantity. This is a review state only; it does not create, save, or transmit an order. A normal trend `SELL` without stop-loss trigger remains `SELL_REVIEW` with no invented partial-sale quantity.
- For `HOLD`, `WATCH`, profile-missing, market-data-unavailable, missing risk-policy, or missing stop-loss cases return an explicit non-ready status/reason rather than a numeric recommendation.
- Responses must include the strategy/provider data provenance already available from the underlying guide, the reference price, stop price where present, currency/market, and a clear `requiresUserConfirmation=true` flag.
- Existing `TradePlan` remains a domain model and must not be persisted or marked broker-submission-ready by this slice.

## Acceptance Checks

- [ ] Focused domain/service tests cover risk amount formula, exposure cap, missing stop-loss, missing risk policy, no cash sync constraint, stop-loss exit review, and ordinary sell review.
- [ ] Controller tests cover success, ownership, and request-free read-only behavior.
- [ ] Existing strategy guide behavior remains unchanged.
- [ ] `./gradlew test --tests '*TradePlan*' --tests '*PortfolioController*'` passes at minimum; report any full-suite result separately.
- [ ] No API keys, credentials, broker calls, commits, pushes, dependency changes, or unrelated refactors.

## Handoff (2026-09-15 추가 작업: 종목별 구체적 행동 초안 목록)

기존 검토용 매매 계획 API의 요약 필드는 모두 유지한 채, 각 종목에 `plannedActions`
순서 목록을 추가했다. 이 목록은 다음 세 가지 결정론적 규칙만 구현한다.

1. 손절가가 설정된 보유 종목 → 현재 가격과 무관하게 항상 `PROTECTIVE_EXIT_REVIEW`
   (보유 수량 전체, 트리거 가격 = 손절가) 행동을 포함한다.
2. 전략 기준 가격이 손절가보다 높은(위험폭이 양수인) 유효한 보유 포지션 →
   `PARTIAL_PROFIT_REVIEW` 행동을 포함하되, **가격·수량은 산출하지 않는다.** 코디네이터
   확인 결과 고정 R배수 등 검증되지 않은 상수는 금지되어(`CLAUDE.md` "검증되지 않은
   손절가·수량 비율·가격 기준을 임의 상수로 구현하지 않는다"), 이번 슬라이스는 "검증된
   익절 규칙 미등록" 사유만 명시한다. 향후 백테스트를 거친 목표가 규칙이 전략 프로필에
   등록되면 같은 행동 유형에 가격·수량을 채워 넣을 수 있다.
3. 이미 보유 중인 종목에 Track A `BUY` 신호가 발생하면 매도가 아니라
   `POSSIBLE_ADD_REVIEW`로 처리한다(top-level `status`도 동일하게 표시하도록
   `TradePlanPreviewStatus.POSSIBLE_ADD_REVIEW`를 신설). 수량은 기존 매수 후보 공식과
   같은 위험 금액 공식(`포트폴리오 평가액 * 주문당 최대 손실 비율 / 위험폭`)을 남은
   종목당 노출 한도(`종목당 최대 노출 금액 - 현재 보유 평가금액`)로 제한한다. 보유 수량
   데이터나 위험 한도·손절가가 없으면 가격·수량을 임의로 만들지 않고 비실행 항목이나
   `NOT_READY`로 반환한다. 현재 `StrategyDecisionMaker.decideForHolding`은 실제로
   `BUY`를 반환하지 않지만, 이 서비스는 방어적으로 이 경우를 처리해야 한다는 작업
   지시에 따라 구현했다.

- Files changed:
  - 신규: `src/main/java/com/tradeguide/domain/strategy/PlannedTradeActionType.java`,
    `PlannedTradeAction.java`,
    `src/main/java/com/tradeguide/dto/strategy/PlannedTradeActionResponse.java`,
    `src/test/java/com/tradeguide/domain/strategy/PlannedTradeActionTest.java`
  - 수정: `domain/strategy/TradePlanPreviewStatus.java`(`POSSIBLE_ADD_REVIEW` 추가),
    `domain/strategy/AssetTradePlanPreview.java`(`plannedActions` 필드 및 하위 호환
    생성자 오버로드 추가), `dto/strategy/AssetTradePlanPreviewResponse.java`,
    `service/strategy/TradePlanPreviewService.java`,
    `test/java/.../domain/strategy/AssetTradePlanPreviewTest.java`,
    `test/java/.../service/strategy/TradePlanPreviewServiceTest.java`,
    `test/java/.../controller/portfolio/PortfolioControllerTest.java`
  - 컨트롤러(`PortfolioController.java`) 자체는 변경 없음 — 기존 위임 코드가 새 필드를
    그대로 직렬화한다.
- Verification run: `./gradlew test --tests '*TradePlan*' --tests '*PortfolioController*'
  --tests '*PlannedTradeAction*'` 전체 통과. `./gradlew test`(전체 백엔드 스위트, 140개
  테스트 클래스) 전체 통과. `./gradlew build -x test`도 통과.
- API / data-model / policy impact: 기존 응답 필드·엔드포인트·상태 코드는 그대로다.
  `AssetTradePlanPreviewResponse`에 `plannedActions: PlannedTradeActionResponse[]`
  배열만 추가되는 순수 추가적(additive) 변경이며, 프런트엔드가 이 필드를 무시해도
  기존 동작에 영향이 없다. `TradePlanPreviewStatus`에 `POSSIBLE_ADD_REVIEW` 열거값을
  추가했다(추가적 변경). DB 스키마·마이그레이션 변경 없음, 브로커 호출 없음, 계획
  저장 없음.
- Open decision or risk: `PARTIAL_PROFIT_REVIEW`는 현재 항상 가격·수량 없이 사유만
  제공한다. 검증·백테스트된 익절 목표가 규칙이 리서치 트랙에서 채택되면 별도 정책
  작업 계약으로 이 항목에 실제 값을 채우는 후속 작업이 필요하다.
