# Agent Task Contract: 세션 예측엔진 데이터 준비 검사기 v1 (사후 기록)

> **사후 기록(2026-09-23)**: 원본 작업 계약은 삭제된 로컬 Orca 작업 공간에만 있었고 저장소에 없었다. 이 문서는 `research/reports/engine-phase1-audit-and-protocol-2026-09-21.md` 7·10절과 `research/reports/session-engine-data-preflight-v1.md`에 남은 내용으로 계약 범위를 재구성한 것이며, 원문 계약서가 아니다. 원문과 다른 세부 조건이 있었을 수 있다.

## Identity

- Task ID: `session-engine-data-preflight-v1`
- Owner: Claude (Codex 조정, Orca task `task_48ef8b6b45e6`, 후속 수정 `task_7cccd3bec280`)
- Work mode: 연구용 오프라인 도구 구현
- Branch / worktree: 격리 Orca 작업 공간 `session-engine-data-preflight`(이후 삭제). 2026-09-23 복구본을 `recovered-research-import-20260923`으로 저장소에 반영.

## Outcome

데이터 manifest를 읽고 데이터 부족·정보 시점 누출·가격 조정 정책 미확정을 탐지하는 오프라인 검사기와 합성 테스트. 데이터셋별 `PASS`/`WARN`/`BLOCKED`, 사유 코드, 근거 필드를 출력한다.

## Allowed Files

- `research/scripts/session-engine-data/**`
- `research/reports/session-engine-data-preflight-v1.md`

## Non-Goals And Guardrails

- 네트워크, 의존성 설치, 모델 학습, 전략 변경, 주문 실행, 커밋·푸시 금지.
- 기존 정책·실험 결과·운영 코드 변경 금지. 실제 API 키·계좌 데이터 사용 금지.
- 합격하지 않은 자료를 정제 명목으로 임의 수정하지 않는다.
- `PASS`는 구조·내부 일관성 통과일 뿐 시장 사실성·라이선스·수익성 인증이 아니다.

## Acceptance Checks

- 검사 항목: `availableAt > cutoffAt`, 불명 시각·조정, 세션 부재, 중복·잘못된 OHLC, 통화 불일치, 필요한 PIT 유니버스 부재.
- 합성 정상·오류 케이스가 모두 기대 상태로 판정되고, 기존 일봉 자료는 분봉 요구를 통과하지 못한다.
- 결과(기록상): 최초 84개 → 다종목 세션 누락 결함 수정 후 90개 통과(Codex 재실행). 이후 수집 근거·재생 게이트·판단 추적 도구가 추가돼 복구본 기준 124개 통과(2026-09-23 Claude 재실행).

## Handoff

- 보고서: `research/reports/session-engine-data-preflight-v1.md`, 후속 `session-collection-contract-v1.md`, `session-backtest-replay-gate-v1.md`, `session-decision-trace-v1.md`.
- 복구 한계: `research/reports/recovered-research-RECOVERY.md`.
