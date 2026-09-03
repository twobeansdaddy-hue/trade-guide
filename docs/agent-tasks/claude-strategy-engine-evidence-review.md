# Agent Task Contract

## Identity

- Task ID: `claude-strategy-engine-evidence-review`
- Owner: `Claude`
- Work mode: `research`
- Branch / worktree: dedicated Claude research worktree from current `main`

## Outcome

Trade Guide의 현재 Track A 주봉 10/40 이동평균 규칙을 기준선으로 두고,
미국 주식과 ETF 의사결정 지원에 적용 가능한 검증 기반 전략군을 비교한다.
사용자가 언급한 RSI, MACD, 분할 매도는 후보일 뿐 자동 채택하지 않는다.

리포트는 "예측"을 약속하지 않고, 장 시작 전 사용자가 검토할 수 있는 신호·근거·한계·데이터
요구사항을 갖춘 다음 엔진 후보를 제안하거나 보류하는 근거를 남긴다.

## Allowed Files

- `research/reports/strategy-engine-evidence-review.md`
- `research/data/tools/strategy_engine_evidence_audit.*`
- `research/data/backtests.json`

읽기 전용 참고 범위: `src/**`, `frontend/**`, `docs/**`,
`research/STRATEGY_ENGINE_POLICY.md`, 기존 `research/reports/**`.

## Non-Goals And Guardrails

- `src/**`, `frontend/**`, `docs/**`, 정책 문서, 설정, Git 상태는 수정하지 않는다.
- API 키, 계정, 개인 포트폴리오, 로컬 설정을 읽거나 기록하지 않는다.
- 자동 주문, 수익 보장, 임의 목표가·손절가·수량 비율을 제안하지 않는다.
- 결과가 유망해 보인다는 이유로 전략을 채택하거나 기존 Track A 규칙을 바꾸지 않는다.
- 연구 근거는 원 논문, 학술 출판물, 거래소·규제기관·지수 제공자 같은 1차 자료를 우선한다.
  출처 URL, 데이터 조정 방식, 분석 기간, 수수료·스프레드·슬리피지 가정을 기록한다.

## Required Review

1. 현재 Track A 10/40 주봉 추세 규칙과 이미 수행한 손절 리서치의 범위·한계를 요약한다.
2. 아래 전략군을 각각 독립 후보로 평가한다. 지표를 단순 조합하거나 사후 최적화하지 않는다.
   - 시간축 모멘텀 또는 추세 추종
   - 횡단면 모멘텀 기반 후보 선별
   - 가치·품질·수익성 기반 후보 선별
   - 변동성 관리 또는 시장 국면 필터
   - 이벤트 위험 회피(실적·거시 발표)를 위한 데이터 보강
3. 각 후보에 대해 실행 규칙, 필요한 원천 데이터, 적용 자산군(Track A/Track B),
   백테스트 가능성, 미래 Flutter와 공유할 API·도메인 영향, 과최적화·생존편향·데이터
   라이선스 위험을 표로 비교한다.
4. RSI, MACD, 볼린저 밴드, 분할 매수·매도는 기존 근거가 충분한지 별도 표로 정리한다.
   근거 부족이면 "채택 보류"로 명시한다.
5. 가장 먼저 백테스트할 후보는 최대 두 개만 제안한다. 각 후보에 대해 사전 고정한 기간,
   유니버스, 진입·청산·리밸런싱 규칙, 수수료·슬리피지 가정, 학습/검증 분리,
   워크포워드 기준, 성공·실패 판정 지표를 작성한다.
6. 기존 전략 정책에 반영할 문구는 **초안**으로만 제안한다. 정책 파일은 수정하지 않는다.

## Acceptance Checks

- [ ] 모든 권고와 보류 판단에 출처와 한계가 있다.
- [ ] 실행 가능한 데이터가 없는 후보는 구현 후보로 추천하지 않는다.
- [ ] 백테스트 후보는 최대 두 개이며, 사후 파라미터 탐색을 금지하는 계획을 포함한다.
- [ ] 기존 `research/data/backtests.json`을 수정하면 새 레코드만 추가하고, 기존 결과를 덮어쓰지 않는다.
- [ ] `git diff --check`가 통과한다.

## Handoff

- Files changed:
- Sources and data assumptions:
- Candidate strategies and non-adopted strategies:
- Verification performed:
- Proposed policy wording only (not adopted):
- Open decisions or risks:
