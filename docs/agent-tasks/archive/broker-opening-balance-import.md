# 증권사 개시 잔고 선택 반영

- Owner: Claude backend
- Scope: `src/main/**`, `src/test/**`

## 목표

저장된 최신 증권사 보유 종목 스냅샷에서 `ONLY_IN_BROKER`인 한 종목을 사용자가 명시적으로 승인하면,
Trade Guide 보유 종목에 **증권사 개시 잔고**로 반영한다.

이는 실제 매수 체결을 재현하거나 자동 동기화하는 기능이 아니다. 기존 수동 거래 기록을 덮어쓰거나
수량 차이를 자동 정정해서는 안 된다.

## 확정 정책

- 승인 대상은 최신 스냅샷의 `ONLY_IN_BROKER` 항목 하나뿐이다.
- `MATCHED`, `QUANTITY_MISMATCH`, `ONLY_IN_TRADE_GUIDE`는 `409 Conflict`로 거부한다.
- 생성되는 원장 행은 `TradeTransactionSource.BROKER_OPENING_BALANCE`와 `TradeType.BUY`를 가진다.
  수량·평단가는 스냅샷 값을 사용하고 수수료는 `0`이다. 반영 시각은 승인 시각이며 실제 체결 시각으로
  표시하지 않는다.
- `PortfolioBrokerHoldingImport`는 승인 당시 수량·평단가·표시명·스냅샷 기준 시각과 생성 거래 ID,
  승인 회원·시각, 상태(`ACTIVE`, `REVOKED`)를 감사 목적으로 보존한다.
- 동일 스냅샷 항목은 DB 유니크 제약으로 중복 승인되지 않아야 한다. 동일 요청 재시도는 기존 승인 결과를
  `200 OK`로 돌려준다.
- 개시 잔고는 전용 취소 API로만 취소한다. 취소는 해당 원장 행을 삭제하고 승인 이력을 `REVOKED`로 남긴다.
  일반 거래 삭제 API는 활성 개시 잔고 행을 삭제할 수 없어야 한다.
- 과거 스냅샷, 다른 포트폴리오 소유 항목, 비활성 자산 카탈로그 종목은 반영하지 않는다.
- 증권사 API 호출·자격 증명 복호화는 이 기능에 절대 추가하지 않는다. 저장된 최신 스냅샷만 사용한다.

## API

- `POST /api/members/{memberId}/portfolios/{portfolioId}/broker-holding-imports`
  - body: `{ "snapshotItemId": 123 }`
  - 새 승인: `201 Created`; 동일 항목 재시도: `200 OK`.
- `GET /api/members/{memberId}/portfolios/{portfolioId}/broker-holding-imports`
  - 최신순 감사 이력 조회.
- `DELETE /api/members/{memberId}/portfolios/{portfolioId}/broker-holding-imports/{importId}`
  - 활성 개시 잔고 취소. 완료 후 `204 No Content`.
- 기존 비교 응답의 각 증권사 항목에는 `snapshotItemId`를 포함한다. Trade Guide에만 있는 항목은 `null`이다.

## 구현 요구사항

1. Flyway 마이그레이션으로 거래 출처와 가져오기 감사 테이블·인덱스·유니크 제약을 추가한다.
2. 기존 수동 거래 행은 `MANUAL` 출처를 가진다. 기존 수동 매매 생성 API의 요청/응답 호환성을 깨지 않는다.
3. 포트폴리오 소유권, 최신 스냅샷 여부, 비교 상태, 활성 상장 자산을 서버 트랜잭션 안에서 재검증한다.
4. 동시 요청으로 중복 거래가 생기지 않게 한다.
5. 기존 스냅샷 비교 조회는 외부 제공자 호출과 복호화 없이 계속 동작해야 한다.
6. 사용자에게 원문 계좌번호, Client Secret, 복호화된 값은 반환하거나 로그에 남기지 않는다.

## 테스트

- 승인 성공 후 거래 행·감사 행·Holding 계산 결과 검증.
- 동일 항목 재시도 멱등성 및 동시성/유니크 제약 검증.
- 과거 스냅샷, 잘못된 비교 상태, 다른 포트폴리오, 비활성 종목의 거부 검증.
- 취소가 원장 반영을 되돌리고 감사 이력을 보존하는지 검증.
- 일반 거래 삭제가 활성 개시 잔고를 거부하는지 검증.
- controller/API 테스트, 전체 Gradle 테스트, PostgreSQL 통합 테스트를 실행한다.

## 금지

- `frontend/**`, `docs/**`(이 작업 계약 제외), 로컬 설정, 시크릿, 의존성, Git 상태를 수정하지 않는다.
- 커밋·푸시·병합하지 않는다.
