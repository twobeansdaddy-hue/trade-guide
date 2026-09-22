# Agent Task Contract

## Identity

- Task ID: `antigravity-transaction-history-adjustment-source-20260916`
- Owner: `Antigravity CLI`
- Work mode: `scoped-implementation`
- Branch / worktree: 현재 브랜치(main)에서 진행, 별도 브랜치 불필요

## Outcome

"매매 기록" 화면(`TransactionHistoryList.tsx`)이 백엔드에 이미 존재하는
`BROKER_HOLDING_ADJUSTMENT` 출처(증권사 잔고 조정 반영 기록)를 인식하지 못하는 통합
격차를 고친다. 현재는 이 출처의 거래가 "직접 등록"으로 잘못 표시되고, 일반 삭제
버튼을 누르면 서버가 422로 거부하는(사용자에게 원인 불명확한 실패로 보임) 상태다.

## Background (읽기 전용 참고, 진단 완료)

- 백엔드는 이미 `BROKER_HOLDING_ADJUSTMENT`를 보호한다
  (`src/main/java/com/tradeguide/service/trade/TradeTransactionService.java:135-138`):
  일반 삭제 API로 지우려 하면 `TradeTransactionProtectedException`(422)을 던진다.
  이 부분은 이미 올바르게 동작하며 변경할 필요가 없다.
- 프론트엔드 타입(`frontend/src/types/tradeTransaction.ts`)의
  `TradeTransactionSource` 유니온에 `"BROKER_HOLDING_ADJUSTMENT"`가 빠져 있다.
- `frontend/src/components/trade/TransactionHistoryList.tsx`의 `sourceLabels`에도
  이 값이 없어 배지가 "직접 등록"으로 잘못 표시되고(`sourceLabels[source] ?? "직접
  등록"` 폴백), `isBrokerOpeningBalance`/`isBrokerOrderHistory` 분기에도 없어서
  일반 "삭제" 버튼이 노출된다 — 클릭하면 서버가 422로 거부해 사용자에게 원인 불명확한
  실패로 보인다.
- 같은 출처의 취소 UI는 이미 `BrokerHoldingSnapshotSection.tsx`의
  `id="broker-holding-adjustments"` 영역(반영 취소 버튼 포함)에 구현되어 있다.
  `BROKER_OPENING_BALANCE`가 `/broker-accounts#broker-holding-imports`로,
  `BROKER_ORDER_HISTORY`가 `/broker-accounts#broker-order-imports`로 안내하는 것과
  같은 패턴으로, `BROKER_HOLDING_ADJUSTMENT`는 `/broker-accounts#broker-holding-adjustments`로
  안내하면 된다.

## Allowed Files

- `frontend/src/types/tradeTransaction.ts`
- `frontend/src/components/trade/TransactionHistoryList.tsx`

## Non-Goals And Guardrails

- 백엔드는 이미 올바르게 동작하므로 수정하지 않는다(읽기 전용 참고).
- 다른 출처(`MANUAL`, `BROKER_OPENING_BALANCE`, `BROKER_ORDER_HISTORY`)의 기존 동작·
  문구·링크는 변경하지 않는다.
- 임의의 새 출처 값을 만들지 않는다. 백엔드 enum(`TradeTransactionSource.java`)에
  이미 있는 4개 값만 다룬다.

## Acceptance Checks

- [x] `TradeTransactionSource` 타입에 `"BROKER_HOLDING_ADJUSTMENT"`가 추가된다.
- [x] `sourceLabels`에 해당 값의 한글 라벨이 추가된다(예: "증권사 잔고 조정",
      기존 다른 라벨과 어조 일관성 유지).
- [x] 이 출처의 거래에는 일반 "삭제" 버튼 대신 `/broker-accounts#broker-holding-adjustments`로
      이동하는 링크가 노출된다(기존 두 출처와 동일한 패턴).
- [x] `npm run lint`, `npm run build` 통과.
- [x] 가능하면 실제 브라우저에서 조정 반영 이력이 있는 상태를 만들어(또는 기존
      스냅샷/조정 데이터로) 배지·링크가 올바르게 뜨는지 확인. 재현 가능한 데이터가
      없으면 코드 레벨 확인으로 대체하고 그 사실을 보고에 명시한다.

## Handoff

- Files changed:
  - `frontend/src/types/tradeTransaction.ts` (`TradeTransactionSource` 유니온에 `"BROKER_HOLDING_ADJUSTMENT"` 추가)
  - `frontend/src/components/trade/TransactionHistoryList.tsx` (`sourceLabels` 매핑에 `"증권사 잔고 조정"` 추가, `isBrokerHoldingAdjustment` 분기를 통해 일반 삭제 버튼 대신 `/broker-accounts#broker-holding-adjustments` 링크 표시, dl 라벨 일관성 처리)
- Verification run:
  - `npm run lint` 통과 (0 errors, 0 warnings)
  - `npm run build` 통과 (Vite 클라이언트 프로덕션 빌드 성공)
  - 실제 브라우저(Headless Chrome CDP) 검증 완료:
    - 포트폴리오의 실제 `BROKER_HOLDING_ADJUSTMENT` 거래(ID 13, SOXL)가 "증권사 잔고 조정" 배지로 표시됨 확인
    - 일반 "삭제" 버튼이 숨겨지고 `/broker-accounts#broker-holding-adjustments`로 연결되는 "잔고 조정 이력에서 반영 취소" 링크가 정상 노출됨 확인
    - 스크린샷 캡처 및 검증 완료 (`holdings-transactions.png`)
- API / data-model / policy impact: 없음 (백엔드는 이미 완료·읽기 전용)
- Open decision or risk: 없음
