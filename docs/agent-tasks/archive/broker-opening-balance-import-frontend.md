# 증권사 개시 잔고 선택 반영 화면

- Owner: Claude frontend
- Scope: `frontend/**`

## 목표

설정 화면의 저장된 증권사 보유 종목 비교에서 `ONLY_IN_BROKER`인 항목 하나를 사용자가
명시적으로 승인해 **개시 잔고**로 반영하거나, 기존 승인 이력을 취소할 수 있게 한다.

자동 동기화나 일괄 반영은 구현하지 않는다. `QUANTITY_MISMATCH`, `MATCHED`,
`ONLY_IN_TRADE_GUIDE`에는 반영 버튼을 노출하지 않는다.

## 확정 API 계약

기준 경로: `/api/members/{memberId}/portfolios/{portfolioId}`

- `POST /broker-holding-imports`, body `{ "snapshotItemId": number }`
  - 새 반영은 `201`, 같은 요청 재시도는 `200`.
- `GET /broker-holding-imports`
  - 승인 이력 배열. 항목은 `id`, `snapshotItemId`, `market`, `ticker`, `displayName`,
    `quantity`, `averagePurchasePrice`, `snapshotSyncedAt`, `tradeTransactionId`,
    `approvedByMemberId`, `approvedAt`, `status: ACTIVE | REVOKED`.
- `DELETE /broker-holding-imports/{importId}`
  - 활성 개시 잔고 취소. `204`.
- 저장된 비교 항목에는 `snapshotItemId: number | null`이 있다.

오류 상태는 기존 `getJsonResponse` / `hasApiStatus` 관례를 따른다. `409`는 현재 비교 상태상
반영 불가, `422`는 활성 자산 카탈로그가 아니어서 반영 불가다.

## UI 요구사항

1. `BrokerHoldingSnapshotSection`을 중심으로 구현한다. `ONLY_IN_BROKER` 항목에만
   `개시 잔고로 반영`을 노출한다.
2. 버튼 선택 후 즉시 API를 호출하지 않는다. 종목명/시장/티커/수량/평단가/저장 시각을 보여주는
   확인 패널 또는 모달을 먼저 표시하고, 아래 문구를 명확히 표시한다.
   - `이 반영은 실제 체결 내역이 아닌 개시 잔고 기록입니다.`
   - `수량 차이는 자동으로 수정하지 않습니다.`
3. 승인 성공 후 비교와 이력을 새로 읽는다. 보유 종목 조회도 새로고침할 필요가 있으면 현재
   화면 구조에 맞는 가장 좁은 방식으로 갱신한다.
4. 활성 이력에는 `반영 취소`를 제공하고, 재확인 후 `DELETE`한다. 취소 성공 후 비교/이력을 갱신한다.
   `REVOKED` 이력은 읽기 전용 상태로 표시한다.
5. 버튼별 pending 상태를 분리하고, 오류와 성공 메시지는 고정 높이 영역을 사용해 폼과 카드가
   흔들리지 않게 한다. 모바일 및 데스크톱에서 입력/버튼 높이와 간격이 기존 설정 화면과 일관돼야 한다.
6. 외부 증권사 호출, 자격 증명 노출, 거래 수량 자동 정정, 다건 선택/일괄 반영은 금지한다.

## 검증

- `npm run lint`
- `npm run build`
- 브라우저에서 다음을 확인한다.
  - `ONLY_IN_BROKER` 1건만 반영 버튼이 보이는지
  - 확인 전에는 API가 호출되지 않는지
  - 성공, `409`, `422`, 취소 후 화면 상태가 명확한지
  - 작은 화면에서 메시지로 레이아웃이 밀리거나 버튼/텍스트가 겹치지 않는지

커밋·푸시하지 않는다.
