# Agent Task Contract: 조건부 가격·수량 계산 코어 v1 (사후 기록)

> **사후 기록(2026-09-23)**: 원본 작업 계약은 삭제된 로컬 Orca 작업 공간에만 있었고 저장소에 없었다. 이 문서는 `research/reports/session-plan-core-progress-2026-09-22.md`와 `research/reports/session-plan-core-v1.md`에 남은 내용으로 계약 범위를 재구성한 것이며, 원문 계약서가 아니다. 원문과 다른 세부 조건이 있었을 수 있다.

## Identity

- Task ID: `session-plan-core-v1`
- Owner: Claude (Codex 조정·독립 재현, Orca task `task_4e8681ffc82a`)
- Work mode: 연구용 오프라인 산식 구현
- Branch / worktree: 격리 Orca 작업 공간 `session-engine-data-preflight`(이후 삭제). 2026-09-23 복구본을 `recovered-research-import-20260923`으로 저장소에 반영.

## Outcome

명시적으로 입력된 가격·수량·한도에 대한 단일 종목·단일 통화의 조건부 매수·분할 매도·보호적 청산 계획 계산과 체결 반영(`build_plan`, `apply_fill`). 실제 가격 예측이나 주문 전송이 아니다.

## Allowed Files

- `research/scripts/session-plan-core/**`
- `research/reports/session-plan-core-v1.md`

## Non-Goals And Guardrails

- 가격·손절가·수량·수수료·위험 한도는 모두 입력값이며 임의 기본값을 만들지 않는다(없으면 `MISSING_FIELD`로 차단).
- 가용 현금은 외부 예약을 이미 차감했다는 명시적 확인이 필요하다.
- 매수·분할 매도·보호적 청산은 서로 배타적인 검토 시나리오이며 동시 예약 주문이 아니다. 체결(부분 체결 포함) 후 상태 버전을 올리고 기존 계획을 무효화한다.
- 기존 `TradePlanPreviewService`·운영 코드·전략 정책·의존성·Git 변경 금지. 네트워크·설치·API 키·실계좌·커밋·푸시 금지.
- 손절 기준 예상 손실은 갭·유동성 부족 시 실제 손실 상한을 보증하지 않는다. 증권사 OCO 지원, 계좌 동시성, 영속적 중복 체결 방지, 복수 종목 자금 배분은 범위 밖이다.

## Acceptance Checks

- 합성 unittest 최소 25개(기록상 계약 조건). 결과: 46개 통과(작업자 보고) → 복구 후 Codex가 상태 버전 최댓값 결함을 재현·수정해 47개(2026-09-23) → 복구본 최신 48개 통과(2026-09-23 Claude 재실행).
- 기존 preflight 회귀 테스트 통과.

## Handoff

- 보고서: `research/reports/session-plan-core-v1.md`, 진행 기록 `research/reports/session-plan-core-progress-2026-09-22.md`.
- 미검토(기록상): 평균단가 수수료 제외·소수 8자리 올림, 현금 소수 정밀도 제약. 운영 통합·주문 활성화·수익성 검증은 하지 않았다.
- 복구 한계: `research/reports/recovered-research-RECOVERY.md`.
