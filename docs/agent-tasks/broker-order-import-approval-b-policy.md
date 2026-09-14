# 증권사 주문 이력 반영 - 개시 잔고 기준 이후 정책

- Owner: Claude backend implementation
- Work mode: scoped implementation
- Task ID: `broker-order-import-approval-after-opening-balance`
- Decision: **Adopted**. 활성 개시 잔고가 있는 포트폴리오에서는 그 개시 잔고의 승인 시각 이후에 체결된 증권사 주문만 원장 반영 후보로 삼는다.

## Product rule

개시 잔고는 사용자가 승인한 시점에 만드는 합성 매수 거래다. 따라서 과거 실제 체결을 함께 넣으면 중복 계상이 생길 수 있다.

1. 활성 `BROKER_OPENING_BALANCE` 거래가 있으면, 가장 최근 승인 시각을 포트폴리오의 주문 이력 반영 기준점으로 사용한다.
2. 기준점보다 이르거나 같은 시각의 주문은 원장 반영 대상이 아니다. 스테이징 결과와 API 응답에는 제외 이유를 보존한다.
3. 기준점 이후 주문만 한 실행(run) 단위로 명시적 승인할 수 있다. 자동 반영은 금지한다.
4. 승인 전에는 기존 원장을 포함해 전체 거래 이력을 재생 검증한다. 초과 매도나 수량 불일치가 발생하면 배치 전체를 거부한다.
5. 이미 반영한 주문은 증권사 계좌 + 안정적 주문 식별자 기준으로 멱등 처리한다. 재승인은 새 거래를 만들지 않는다.
6. 승인 취소는 전용 경로로만 가능하며, 일반 매매 삭제 API는 증권사 주문 이력 출처를 삭제하지 못한다.
7. 이번 단계 범위는 US/USD, 체결 완료 주문만이다. 배당·입출금·KR 통화는 제외 건수와 이유만 보여 주고 원장에 쓰지 않는다.

## Backend scope

Allowed write paths:

- `src/main/java/**`
- `src/test/java/**`
- `src/main/resources/db/migration/**`

Implement the approval/revoke API for the existing broker-order-import preview/run model. Reuse existing staged order, reconciliation, account, and opening-balance audit patterns; do not invent a provider endpoint and do not change broker credential handling.

Required API behavior:

- explicit `POST .../broker-order-imports/{runId}/approval`
- explicit `DELETE .../broker-order-imports/{runId}/approval`
- no transaction ledger write when a run has no eligible post-baseline items
- return a clear 409 conflict when active opening-balance baseline leaves no eligible items, the run has a reconciliation mismatch, or replay validation fails
- the response must expose only safe counts/timestamps/statuses, never credentials, tokens, account sequence, or provider raw errors

## Required tests

- baseline filters historical orders and keeps them out of the ledger
- approval creates `BROKER_TRANSACTION_HISTORY` rows using provider execution timestamps
- approval is atomic and idempotent under a retry/concurrent duplicate attempt
- no eligible post-baseline rows and reconciliation mismatch both reject without writes
- revoke restores prior state and preserves audit history
- generic transaction delete rejects broker-history rows
- controller access/error mapping and PostgreSQL/Flyway coverage where applicable

## Non-goals

- no automatic or scheduled sync
- no order placement
- no frontend changes in this task
- no commits, pushes, dependency installation, secret reads, or edits outside the scope

## Handoff

Report in Korean: changed files, API contract, tests passed/not run, migration impact, and remaining user-facing risks.
