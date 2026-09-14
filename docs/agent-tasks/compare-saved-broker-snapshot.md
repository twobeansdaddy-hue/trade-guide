# 저장된 증권사 스냅샷 비교

- Owner: Claude backend
- Scope: `src/main/**`, `src/test/**`

## 목표

저장된 최신 증권사 보유 종목 스냅샷과 Trade Guide 보유 종목을 비교한다.
이 조회는 외부 증권사 호출, 자격 증명 복호화, `TradeTransaction` 또는 `Holding` 변경을 해서는 안 된다.

## 구현 계약

- `GET /api/members/{memberId}/portfolios/{portfolioId}/broker-holding-snapshots/latest/comparison`
- 최신 스냅샷이 없으면 기존 `BrokerHoldingSnapshotNotFoundException`의 404를 유지한다.
- 기존 `BrokerHoldingPreview`, `BrokerHoldingPreviewResponse`,
  `BrokerHoldingPreviewCalculator`를 가능한 한 재사용한다.
- 응답의 조회 시각은 저장된 스냅샷의 `syncedAt`이다.
- `provider`, 연결 ID, 마스킹 계좌, 비교 항목, 미지원 시장 수를 포함한다.
- 기존 `POST /broker-sync-preview`는 외부 증권사 실시간 미리보기로 그대로 유지한다.

## 검증

- 저장된 스냅샷 비교가 제공자 호출 없이 작동하는 서비스 테스트
- 최신 스냅샷 없음 404, 소유권, 항목 비교 API 테스트
- 전체 Gradle 테스트와 PostgreSQL 통합 테스트

## 금지

- 매매 기록, Trade Guide 보유 종목, 증권사 연결 정보, 프론트엔드, 시크릿, 커밋, 푸시를 수정하지 않는다.
