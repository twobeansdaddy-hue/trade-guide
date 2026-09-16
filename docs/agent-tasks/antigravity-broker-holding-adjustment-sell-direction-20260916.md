# Agent Task Contract

## Identity

- Task ID: `antigravity-broker-holding-adjustment-sell-direction-20260916`
- Owner: `Antigravity CLI`
- Work mode: `scoped-implementation`
- Branch / worktree: 현재 브랜치(main)에서 진행, 별도 브랜치 불필요

## Outcome

증권사 잔고 조정(`QUANTITY_MISMATCH`) 화면에서 **원장 수량이 증권사 수량보다 많은
경우**(초과분 매도 조정)도 증권사 수량이 더 많은 경우와 동일하게 사용자가 승인
버튼으로 반영할 수 있게 한다. 백엔드는 이미 두 방향을 모두 지원하도록 구현·테스트
완료되었으며, 이 작업은 프론트엔드 표시/승인 흐름만 다룬다.

## Background (읽기 전용 참고)

- 백엔드 변경: `src/main/java/com/tradeguide/service/broker/PortfolioBrokerHoldingAdjustmentWriter.java`
  - 증권사 수량 > 원장 수량: 기존과 동일하게 조정 매수(BUY) 생성.
  - **원장 수량 > 증권사 수량(신규)**: 조정 매도(SELL)를 생성한다. 실제 체결가를 알 수
    없으므로 단가는 원장의 조정 전 평균 매입가와 동일하게 둬 실현손익을 0으로 만든다.
    원장 평균 매입가는 조정 후에도 그대로 유지된다.
  - 두 수량이 이미 같으면 여전히 422(`BrokerHoldingAdjustmentUnprocessableException`)로
    거부한다(조정 불필요).
- API 계약은 변경되지 않았다. 기존 `POST .../broker-holding-adjustments`
  (`{snapshotItemId}`)와 `GET/DELETE`가 그대로 두 방향 모두를 처리한다. 방향은
  서버가 스냅샷의 `brokerQuantity`와 원장 수량을 비교해 자동으로 결정한다.
- 관련 테스트: `src/test/java/com/tradeguide/service/broker/PortfolioBrokerHoldingAdjustmentWriterTest.java`
  의 `createsSellAdjustmentWhenLedgerQuantityExceedsBroker`.

## Allowed Files

- `frontend/src/components/broker/BrokerHoldingSnapshotSection.tsx`
- `frontend/src/App.css` (이 화면에 필요한 스타일만)

## Non-Goals And Guardrails

- API 계약, 백엔드 로직, DB 스키마는 변경하지 않는다(이미 완료됨, 읽기 전용).
- 실제 증권사 주문을 발생시키는 어떤 요소도 추가하지 않는다. 이 화면은 항상
  "Trade Guide 내부 잔고 조정 기록만 생성, 실제 증권사 주문 아님"을 명시해야 한다.
- 조정 매도의 단가·손익을 임의로 계산해 화면에 표시하지 않는다. 서버 응답
  (`unitPrice`, `deltaQuantity` 등)을 그대로 사용한다.
- 영문 enum(`SELL`, `BUY`, `QUANTITY_MISMATCH` 등)을 사용자 화면에 그대로 노출하지 않는다.

## Acceptance Checks

- [ ] `comparisonItems`에서 `item.comparison === "QUANTITY_MISMATCH"`이고
      `tradeGuideQuantity > brokerQuantity`(현재 `isNegativeOrEqualMismatch`로 처리되던
      케이스 중 `deltaQuantity < 0`인 경우만)일 때, 기존의 "자동 잔고 조정을 지원하지
      않습니다" 안내 대신 승인 버튼(예: "초과분 매도 조정 반영")을 노출한다.
  - `deltaQuantity === 0`(수량이 이미 같은 특수 케이스)은 여전히 액션 없이 안내만
    표시해도 무방하다(서버가 422로 거부하는 상태와 일치시킨다).
- [ ] 확인 패널에 매도 방향임을 명확히 표시한다(예: "-{수량}주", "증권사 {brokerQty} /
      원장 {tgQty}"). 기존 매수 확인 패널의 "+{수량}주" 표기와 혼동되지 않게 한다.
- [ ] 확인 패널에 "실제 체결가를 알 수 없어 원장 평균 매입가를 그대로 사용해 실현손익
      없이 처리합니다"와 같은 안내 문구를 포함한다.
- [ ] "자동 주문 없음, 실제 증권사 주문 아님"과 "5단계 이력에서 언제든 취소 가능" 안내는
      매수 케이스와 동일하게 유지한다.
- [ ] 승인 후 조정 이력 목록(이미 있는 `broker-holding-adjustments` 영역)에서 매수/매도
      두 방향이 구분되어 표시된다(부호 또는 라벨로).
- [ ] `npm run lint`, `npm run build` 통과.
- [ ] 데스크톱과 375px/360px 폭에서 새 버튼·확인 패널이 가로 스크롤·잘림·겹침 없이
      표시되는지 확인.
- [ ] 실제 API(`approveBrokerHoldingAdjustment`)를 호출해 매도 방향 승인·취소가 화면에서
      정상 동작하는지 수동 확인(가능하면 브라우저로 실증).

## Handoff

- Files changed:
- Verification run:
- API / data-model / policy impact: 없음(백엔드는 이미 반영·테스트됨)
- Open decision or risk:
