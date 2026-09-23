# Agent Task Contract: 예측엔진 D1 데이터 커버리지 실측과 브로커 기능 조사

## Identity

- Task ID: `engine-d1-data-coverage-20260923`
- Owner: Claude (Codex 휴업 중 대행). 자격 증명이 필요한 실측 실행은 사용자.
- Cross verifier: 사용자
- Work mode: 리서치 (읽기 전용 조사 + 오프라인 실측 도구)
- Branch / worktree: `main` 작업 트리

## Outcome

`research/reports/trade-guidance-engine-design-2026-09-21.md` §14의 미결정 사항 중 2026-09-23 사용자 결정에 따라 무료 소표본으로 데이터 확보 가능 범위를 측정하고, 토스 공개 문서로 주문 기능(세션·조건주문·부분 체결 후 처리) 지원 표를 만든다. 결과로 합격 수치 후보를 제안할 근거를 모은다.

2026-09-23 사용자 결정:
1. 보유 기간: 설계안 유지(후보 20거래일, 주문 계획 다음 세션). 1차 실험은 일봉 기반 다음 세션 고가·저가·종가 분위수 예측(D1)까지, 체결 검증(D2)은 분봉 확보 후.
2. 데이터: 무료 소표본 실측 먼저, 유료 구매는 결과 후 별도 결정.
3. 브로커: Claude가 공개 문서 조사, 계정 단위 확인은 사용자. 확인 전 복합 조건주문은 "예약 불가".
4. 성과 합격 수치: 데이터 실측 후 첫 실험 전에 Claude 제안, 사용자 확정.

## Allowed Files

- `docs/agent-tasks/engine-d1-data-coverage-20260923.md`
- `research/TASKS.md` (§9 결정 기록)
- `research/reports/toss-broker-order-capability-survey-20260923.md`
- `research/reports/engine-data-coverage-probe-20260923.md`
- `research/scripts/engine-data-probe/**`

## Non-Goals And Guardrails

- 모델 학습, 백테스트 성능 실험, 전략 채택, 운영 코드·설정 변경을 하지 않는다.
- 주문·계좌 API를 호출하지 않는다. 브로커 조사는 공개 문서만 읽는다.
- Claude는 자격 증명을 입력·취급하지 않는다. 실측 도구는 환경변수로만 키를 읽고 키·토큰·응답 원문을 출력하지 않으며, 실행은 사용자가 한다.
- SEC 요청의 식별용 User-Agent는 사용자가 정한 문자열만 사용한다. 임의 연락처를 만들지 않는다.
- 공개 문서와 실측 결과를 구분하고, 문서에 없는 기능을 지원된다고 쓰지 않는다. 문서·데이터 공급자의 이용 조건도 함께 기록한다.
- Claude는 푸시하지 않는다.

## Acceptance Checks

- [x] `research/TASKS.md` §9에 네 가지 결정이 기록된다.
- [x] 토스 주문 기능 조사 보고서: 기능별 지원/미지원/문서 불명과 출처 URL·접근일.
- [ ] 분봉 이력·SEC 접수 시각·거시 vintage 실측 결과(수행분과 미수행분 구분). — 분봉 1차 완료, SEC·ALFRED 미수행.
- [ ] 결과를 근거로 한 합격 수치 후보(사용자 확정 전).

## Handoff

- Files changed(1차, 2026-09-23): 이 계약, `research/TASKS.md` §9, `research/reports/toss-broker-order-capability-survey-20260923.md`, `research/reports/engine-data-coverage-probe-20260923.md`, `research/scripts/engine-data-probe/minute_depth_probe.py`.
- Verification run: 실측 도구를 모의 서버로 확인한 뒤 사용자가 실제 토스·Twelve Data로 실행(2026-09-23 15:34 KST). 공개 문서는 OpenAPI 명세 1.2.17 기준.
- API / data-model / policy impact: 없음(리서치).
- Open decision or risk: SEC 식별 User-Agent 문자열과 FRED API 키 발급 여부(사용자). 후속 실측 3건(국내 2022년 봉 거래량, 미국 프리마켓 포함 여부, Twelve Data 1분봉 이력 깊이). 공개 문서와 2026-09-23 일봉 1차 관측 가설의 불일치는 2차 관측 후 판단.
