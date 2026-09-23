# Agent Task Contract: 복구 연구 산출물 저장소 반영

## Identity

- Task ID: `recovered-research-import-20260923`
- Owner: Claude (Codex 휴업 중 대행)
- Cross verifier: 사용자
- Work mode: `scoped-implementation` (리서치 산출물 반영)
- Branch / worktree: `main` 작업 트리

## Outcome

저장소 밖 로컬에만 있던 예측엔진(조건부 매매 가이드 엔진) 설계·데이터 감사 문서와 오프라인 연구 도구 복구본을 저장소 `research/`로 가져와, 저장소만 읽는 에이전트도 현재 엔진 방향을 알 수 있게 하고 원격 백업이 없던 위험을 없앤다. 2026-09-23 사용자 결정: 부록 A의 B안(파일만 단일 커밋, 이력 제외).

- 원본: `~/.codex/.chatgpt-projects/…/outputs/recovered-research`(로컬 Git, 최신 `765979e`)와 같은 `outputs/`의 설계·프로토콜·진행 문서 3개.
- 근거: `docs/design/PORTFOLIO_STATE_SNAPSHOT_PROPOSAL.md` 부록 A.

## Allowed Files

- `docs/agent-tasks/recovered-research-import-20260923.md`, `docs/agent-tasks/session-engine-data-preflight-v1.md`, `docs/agent-tasks/session-plan-core-v1.md`
- `research/scripts/session-engine-data/**`, `research/scripts/session-plan-core/**`
- `research/reports/`의 지정 파일 12개(session-*·soxl-cache-data-audit·recovered-research-RECOVERY·service-guide-evidence-integration-design·trade-guidance-engine-design·engine-phase1-audit-and-protocol·session-plan-core-progress)
- `research/TASKS.md` (§9 항목 추가)

## Non-Goals And Guardrails

- 스크립트·보고서 22개는 원문을 바꾸지 않는다(`cp` 후 `cmp`로 동일성 확인). 머리말 추가와 깨진 로컬 절대 경로 링크 교체는 새로 가져오는 문서 5개에만 한다.
- 복구본의 원본 동일성은 인증할 수 없다(`RECOVERY.md`의 한계 유지). 가져오는 것은 연구 도구이며 운영 코드·정책·전략 채택이 아니다.
- `outputs/recover_research.py`는 로컬 대화 기록 경로에 묶인 일회성 도구라 가져오지 않는다.
- 비밀값을 포함하지 않는다(가져오기 전 패턴 검색 0건). 네트워크 호출, 의존성 설치, 운영 코드·설정 변경을 하지 않는다. Claude는 푸시하지 않는다.

## Acceptance Checks

- [x] 원문 복사 22개가 원본과 바이트 단위로 같다(`cmp` 불일치 0건).
- [x] 저장소 경로에서 연구 도구 테스트가 복구본과 같은 수(124개·48개)로 통과한다.
- [x] 새 문서 5개에 상태 머리말이 있고, 저장소에 없는 로컬 절대 경로 링크가 남지 않는다.
- [x] 사후 작업 계약 2개와 `research/TASKS.md` §9 항목이 추가된다.
- [x] `git diff --check` 통과, 사용자 소유 `research/scripts/track-a-infinite-buy/results/`는 건드리지 않는다.

## Handoff

- Files changed: 원문 복사 22개(`research/scripts/session-engine-data/**` 12개, `research/scripts/session-plan-core/**` 4개, `research/reports/` 보고서 6개), 머리말 추가 문서 5개(`recovered-research-RECOVERY.md`, `service-guide-evidence-integration-design-2026-09-23.md`, `trade-guidance-engine-design-2026-09-21.md`, `engine-phase1-audit-and-protocol-2026-09-21.md`, `session-plan-core-progress-2026-09-22.md`), 사후 계약 2개, 이 계약, `research/TASKS.md` §9.
- Verification run: `cmp` 22개 동일, `python3 -m unittest` 124개·48개 통과(`PYTHONDONTWRITEBYTECODE=1`, `__pycache__` 미생성), `git diff --check` 통과. 2026-09-23 Claude 실행.
- API / data-model / policy impact: 없음(연구 문서·오프라인 도구).
- Open decision or risk: 복구본의 원본 동일성은 인증 불가. 사후 계약은 보고서 기반 재구성이며 원문 계약서가 아니다. 설계 §14의 결정 사항(보유 기간, 데이터 확보 범위·비용, 브로커 지원, 성과 합격 수치)은 미결정.
