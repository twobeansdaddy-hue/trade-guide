# Agent Task Contract

## Identity

- Task ID: `claude-track-a-infinite-buy-research-20260921`
- Owner: `Claude`
- Work mode: `research`
- Branch / worktree: 현재 브랜치(main), 별도 브랜치 불필요

## Outcome

"무한매수법 V4.0"(외부 카페 방법론)을 일반 원리 수준으로 재정의해 SOXL/TQQQ 일봉
실데이터로 시뮬레이션하고, 기존 Track A(주봉 10/40 추세)와 단순 보유 대비 우열을
판정한다. 목적은 이 기법을 채택하는 것이 아니라 "엔진의 가격 사다리(목표 C)와
`quantityRatio` 산식에 쓸 만한 검증된 구성요소가 있는지"를 가려내는 것이다.
채택 여부는 리서치 결과와 사용자의 명시적 결정 이후로 미룬다.

## Background (읽기 전용)

- 출처: 카페 게시글 2건(일반모드, 소진 후 리버스모드). 원문에 재가공·재배포 금지
  안내가 있으므로 **원문 규칙·표·문장을 리포지토리 문서에 옮기지 않는다.** 리포트에는
  시뮬레이션에 쓴 일반화된 규칙(분할 매수, 평단 기준 부분 매도, 소진 시 감량)을 우리
  표현으로 정의하고 파라미터는 전부 "탐색 대상"으로 다룬다.
- 검증 대상 구성요소(가설 H1~H4):
  - H1 소진율 기반 동적 목표가(평단 기준선이 투입 비중에 따라 하향)로 사이클 수익이
    개선되는가
  - H2 예산 ÷ 분할 수 사이징이 손절폭 사이징 없이도 최대손실을 제한하는가
  - H3 소진 시 시간 기반 점진 감량(리버스)이 가격 기준 손절(기각 4종)보다 낫거나
    최소한 덜 나쁜가
  - H4 Track A 40주선 필터(위일 때만 신규 사이클 개시)를 결합하면 낙폭이 줄어드는가
- 기존 리포트 재사용: `track-a-real-cost-and-tax-revalidation.md`(비용·세금 가정),
  `soxl-volatility-decay.md`(감쇠·MDD), `track-b-market-concentration-diagnosis.md`
  (표본 편향 경고).
- 데이터: `research/data/cache/soxl-daily-*-2010-2026.csv` 존재. TQQQ 일봉이 없으면
  기존 fetch 스크립트 방식으로 수집하고 제공자·조정 방식·기준일을 metadata에 기록한다.

## Allowed Files

- `research/scripts/track-a-infinite-buy/**` (신규)
- `research/reports/track-a-infinite-buy-*.md` (신규)
- `research/data/cache/tqqq-*` (신규, 데이터 수집 시)
- `research/data/backtests.json` (항목 추가만)
- `research/TASKS.md` (진행 상태 갱신만)
- `docs/agent-tasks/claude-track-a-infinite-buy-research-20260921.md` (이 문서)

`.claude/agent-scope.json`의 `allowedPaths`에 위 `research/` 경로가 없으면 착수 전
사용자가 범위를 열어야 한다(Claude는 `.claude/`를 수정할 수 없다).

## Non-Goals And Guardrails

- `src/**`, DB, API, 정책 문서(`STRATEGY_ENGINE_POLICY.md`)는 읽기 전용이다. 결론이
  채택 후보로 나와도 구현·정책 반영은 별도 계약과 사용자 결정 이후다.
- 체결 가정은 일봉 종가 기준 LOC 근사를 기본으로 하고, 낙관/보수 두 가지 체결 가정을
  모두 돌려 민감도를 기록한다. 실제 증권사 LOC/MOC 지원 여부는 검증하지 않는다.
- 수수료·슬리피지·세금은 기존 재검증 리포트의 가정을 그대로 재사용한다(임의 변경 금지).
- 파라미터 탐색은 학습/검증 구간을 사전 고정한 워크포워드로 한다. 전 구간 최적
  파라미터를 그대로 성과로 보고하지 않는다.
- 표본 편향(2010년 이후 기초지수 강세)을 caveats에 명시하고, 가능하면 2022년
  하락 구간과 2021~22 SOXL 낙폭 구간을 별도 분해해 보고한다.
- 다른 에이전트(Codex)의 리서치 산출물과 파일이 겹치면 수정하지 않고 보고한다.
- 외부 자격 증명·개인 정보는 다루지 않는다.

## Acceptance Checks

- [x] 일봉 시뮬레이터가 사이클(시작~보유 0)·소진·리버스 진입/복귀를 정확히 추적하고,
      소규모 수기 계산 사례 3개 이상과 일치하는 단위 검증이 있다(6개 사례 통과)
- [x] SOXL/TQQQ × 20/40분할 × (기본 / 리버스 없음 / 40주선 필터 결합)을 단순 보유,
      Track A 주봉 규칙과 같은 기간·비용 가정으로 비교한 표(96개 격자, FAS/TNA 교차검증 추가)
- [x] 지표: 사이클 수, 소진 빈도, 리버스 체류 기간, 사이클별 손익 분포(꼬리 손실 포함),
      최대낙폭, 최종 원금, 워크포워드 검증 구간 성과 + 평균 노출과 동일 노출 벤치마크(추가)
- [x] H1~H4 각각에 채택 후보 / 추가 검증 필요 / 기각 판정과 근거
- [x] confidence와 caveats(표본 편향, 체결 가정, 생존 편향) 기록
- [x] `research/data/backtests.json`에 항목 추가, `research/TASKS.md` 갱신

## Handoff

- Files changed: 신규 `research/scripts/track-a-infinite-buy/`(시뮬레이터, 단위 테스트,
  러너, 분석 3종, backtests 추가 스크립트), `research/reports/track-a-infinite-buy-averaging-cycle-validation.md`,
  이 계약 문서. 수정 `research/data/backtests.json`(항목 1개 추가), `research/TASKS.md`(절 9-C 진행 기록).
- Verification run: `test_averaging_cycle_sim.py` 6개 통과, 러너 392개 작업 정상 종료,
  리포트 수치는 분석 스크립트 출력과 대조 후 범위 표현 7곳 정정. 백엔드·프론트엔드는
  변경하지 않아 테스트를 돌리지 않았다.
- API / data-model / policy impact: 없음(리서치 전용). 정책 문서는 읽기 전용으로 유지했고
  결론은 "채택 비추천, 사다리 설계 후보만 보존"이다.
- Open decision or risk: (1) `research/scripts/track-a-infinite-buy/results/`(13MB, 재생성 가능)와
  `__pycache__/`는 삭제 권한이 없어 남아 있으니 **커밋에서 제외**할 것. (2) N=40 예산 분할 구조를
  가격 사다리 후보로 이어갈지는 사용자 결정. (3) Codex 리서치와 파일 충돌은 없었으나 그 주제는 확인하지 못했다.
