# Trade Guide 학습 로그

## 목적

- Java와 Spring Boot 기반 백엔드 개발 역량을 회복한다.
- 미국 주식 포트폴리오에 대해 전략 판단과 예약 주문 검토용 가이드를 제공하는 웹 서비스를 만든다.
- 서비스는 자동 주문을 실행하지 않으며, 최종 투자 판단과 주문은 사용자가 수행한다.

## 작업 환경과 동기화 원칙

- 회사 MacBook과 집 Windows PC에서 각각 로컬 저장소를 사용한다.
- GitHub 원격 저장소가 소스와 문서의 기준이다.
- 한 PC에서 작업을 마친 뒤 `test -> commit -> push`를 수행한다.
- 다른 PC에서는 작업 전에 `git pull --ff-only`로 원격 변경 사항을 먼저 반영한다.
- 두 PC에서 같은 브랜치를 동시에 수정하지 않는다.
- `application-local.yml`, `.env`, API Key, H2 인메모리 데이터는 PC별 로컬 상태이며 동기화하지 않는다.

새 AI 작업은 먼저 `AGENTS.md`, `docs/PROJECT_CONTEXT.md`, 이 문서를 읽는다. `AGENTS.md`는 학습 방식과 작업 원칙을, `PROJECT_CONTEXT.md`는 제품·전략·설계 결정을, 이 문서는 현재 작업 위치를 관리한다.

## 작업 시작과 종료

### 작업 종료

```bash
git status
./gradlew test
git add .
git commit -m "변경 내용"
git push
```

### 다른 PC에서 작업 시작

```bash
git status
git switch feature/candidate-entry-window
git pull --ff-only
./gradlew test
```

로컬 변경 사항이 남아 있으면 먼저 커밋하거나 처리한 뒤 `pull`한다.
기능 브랜치가 `main`에 병합된 뒤에는 최신 `main`에서 새 `feature/...` 브랜치를 만든다.

## 학습 진행 방식

이 프로젝트의 기본 작업 방식은 **학습 우선 모드**다. 기능 완성 속도보다 사용자가 구현 이유와 데이터 흐름을 설명하고, 다음 작은 변경을 직접 수행하는 것을 우선한다.

각 학습 단위는 다음 순서를 따른다.

1. 한 가지 개념과 한 가지 작은 완료 조건을 정한다.
2. 관련 기존 코드를 읽고, 변경할 파일과 데이터 흐름을 먼저 이해한다.
3. 사용자가 요구사항과 힌트를 바탕으로 직접 구현을 시도한다.
4. AI가 코드를 검토하고, 필요한 경우에만 최소 수정안을 제공한다.
5. lint, build, 단위/API 테스트 또는 수동 동작 확인을 수행한다.
6. 다음 기능으로 넘어가기 전에 구현한 내용의 목적·흐름·검증 방법을 짧게 회상한다.

학습 세션의 종료 시점은 사용자가 정한다. AI는 임의로 학습을 종료하거나 다음 세션으로 미루지 않으며, 사용자가 계속 진행하겠다고 하면 현재 이해 수준에 맞는 다음 작은 학습 단위를 이어서 제시한다.

학습은 실무형 기준으로 진행한다. 여기서 실무형이란 결과물의 완성도만이 아니라, 현업에서 반복하는 요구사항 정리, API 계약 확인, 역할 분리, 상태·오류 처리, 코드 리뷰, 테스트, 기능 단위 Git 흐름을 직접 연습하는 것을 뜻한다. 문법을 지나치게 잘게 쪼개기보다, 작은 기능 하나를 요구사항 -> API 계약 -> 상태와 UI -> 오류 처리 -> lint/build/수동 검증까지 완결한다. 설명은 단계적으로 하되, 의도적으로 lint나 build가 깨진 중간 상태를 다음 작업까지 남기지 않는다. 코드 리뷰, 오류 분석, API 계약 대조와 수동 검증을 단순 암기 퀴즈보다 우선한다. 현재 프로젝트 규모에 맞지 않는 절차나 과도한 추상화를 흉내 내지는 않는다.

전체 파일 복붙은 환경 설정, 반복 보일러플레이트, 사용자가 명시적으로 요청한 경우에만 사용한다. 핵심 로직·상태 관리·비동기 처리·도메인 규칙은 전체 정답을 먼저 제시하지 않는다. 사용자가 `빠른 구현 모드`를 명시하면 속도를 높일 수 있지만, 핵심 설계와 검증은 생략하지 않는다.

## 현재 구현 상태

### 작업 방식 전환

2026-09-01부터 이 프로젝트는 학습 우선 모드에서 에이전트 개발 모드로 전환했다. 목표는 기존 Java/Spring Boot와 React/TypeScript 구조를 바탕으로 웹서비스 MVP를 빠르게 완성하는 것이다. Claude Design으로 사이트 디자인을 수립하고, Codex는 설계 검토, 구현, 테스트, 문서화를 주도한다. 향후 Flutter 앱을 고려해 백엔드 HTTP API와 도메인 모델을 웹 UI와 분리해 유지한다.

2026-09-02부터 다중 AI 협업은 역할과 파일 범위를 분리한다. Codex는 구현 통합과 품질 관문, Claude는 리서치·디자인·명시 범위 구현, Gemini는 독립 UX/문서 검토를 담당한다. Claude Code는 작업마다 로컬 쓰기 허용 경로를 받은 경우에만 구현을 수정하며, 기본값은 `research/**`만 쓸 수 있는 안전한 리서치 모드다. 기준 문서는 `docs/AI_COLLABORATION_POLICY.md`이며 실행 방법은 `SETUP.md`에 기록한다.

2026-09-02에 Claude의 범위 제한 디자인 검토를 `docs/design/WEB_MVP_DESIGN_REVIEW.md`로 반영했다. 첫 P0 구현은 위험 한도 미설정 404를 설정 안내로 표시하고, 포트폴리오별 조회 상태를 분리해 이전 포트폴리오 데이터가 남지 않게 하며, 조회 오류에 재시도 버튼과 매매 기록 진입점을 추가하는 것이다. 신규 API, 투자 규칙, 통화 정책은 추가하지 않았다.

### 완료

- 회원, 포트폴리오, 매매 기록, 보유 종목 계산
- 현재가와 포트폴리오 평가 조회
- Twelve Data 일봉·주봉 조회
- Track A 주봉 10/40 이동평균 전략 가이드
- 미국 동부시간 다음 거래일 기준 장전 가이드 스냅샷 저장·조회와 보유·후보별 실행 결과 보존
- 주말·미국 주요 휴장일을 제외한 장전 가이드 자동 생성 스케줄러 골격
- 완료된 주봉만 전략 판단에 반영
- 전략 ID, 버전, 데이터 기준일 응답
- 전략 판단 응답에 신뢰도(`confidence`)와 주의사항(`caveats`) 메타데이터 포함
- 전략 판단 응답에 매수 시점 상태·설명과 손절 상태·설명을 항상 포함
- 추세 상태와 교차 이벤트를 함께 반환하는 Track A 전략 판단
- 시장 데이터의 `StrategySignal`과 보유 종목 맥락의 `StrategyDecision` 분리
- 가장 최근 이동평균 교차 이후 경과 주(`weeksSinceCross`) 계산
- `StrategyDecisionMaker`로 행동 판단 책임 분리
- 보유 종목은 `HOLD`/`SELL`, 미보유 후보는 상승 추세의 교차 후 0~4주에만 `BUY`, 그 외에는 `WATCH`
- Twelve Data와 Yahoo 조정 종가의 백테스트 차이를 감사 기록으로 남기고, Track A 후보 `BUY` 기간을 0~4주로 재검토·채택
- 초기 학습용 `TradeGuideCalculator`는 현재 전략 엔진과 분리된 단순 계산 예제로 유지
- 보유 종목 여부를 반영한 포트폴리오 전략 가이드
- 등록된 `TRACK_A` 자산에서 포트폴리오 보유 종목을 제외한 후보 전략 가이드 조회 API
- 최신 완료 주봉 기준일을 사용하는 전략용 주봉 이력 캐시
- 보유 종목·후보 전략 가이드의 종목별 시장 데이터 실패 목록 응답
- 다종목 전략 가이드에서 429 요청 제한 후 남은 외부 조회 중단
- 포트폴리오 보유 종목별 평가금액 노출 비중 계산 및 조회 API
- 포트폴리오 위험 한도 대비 종목별 노출 초과 경고 조회 API
- Twelve Data SOXL 주봉 스냅샷 기반 Track A 골든 테스트
- 완료 주봉의 신선도 검증과 오래된 데이터의 502 응답
- 증권사 연결·계좌 선택·보유 종목 스냅샷 저장과 비교
- 개시 잔고의 명시적 승인 반영·취소 및 감사 이력
- 토스증권 주문 이력의 읽기 전용 미리보기·정합성 대조·사용자 승인 반영
- 주문 이력의 부분 체결·취소/거부 체결분·중복 의심·수동 기록 겹침 판정
- 증권사 자격 증명 키 버전 로테이션과 의심 항목 사용자 재판정
- Track A 미보유 후보 가이드와 후보 영역의 검토용 UX 분리
- `144091d refactor: 시장 신호와 포트폴리오 결정 분리` 커밋 및 전체 테스트 통과
- 오토매매 opt-in 동의(`BrokerOrderExecutionGrant`)·감사로그(`BrokerOrderExecutionRun`)
  데이터 모델과 API(`docs/agent-tasks/claude-broker-order-execution-grant-implementation-20260917.md`).
  전략 화이트리스트(`track-a-weekly-ma-crossover`만 허용)·포지션 한도(20%)·일일 주문
  한도(10건) 하드 상한을 서비스 계층에서 검증. 킬스위치(PAUSED/REVOKED)를 재개
  경로보다 먼저 테스트. **실제 브로커 주문 제출 로직은 포함하지 않음** - 다음
  슬라이스 범위.
- 오토매매 드라이런 오케스트레이션(`BrokerOrderExecutionService`)
  (`docs/agent-tasks/claude-broker-order-execution-dry-run-implementation-20260917.md`).
  안전장치 4단계(동의 ACTIVE→전략 일치→포지션 한도→일일 주문 한도)를 고정 순서로
  검증 후, `tradeguide.broker.order-execution.live-enabled`(기본값 false)가 꺼져
  있으면 `BrokerOrderExecutionRun`에 `dry_run=true`로만 기록하고 브로커를 호출하지
  않는다. `BrokerOrderSubmissionProvider` 인터페이스와 `ORDER_SUBMISSION`
  capability 관문을 추가했지만 **Toss 구현체는 아직 없어 실거래는 어차피 불가능**
  (이중 안전장치). 전체 테스트 스위트(1071개) 통과 확인.
- 오토매매 매도(SELL) 트리거 스케줄러(`BrokerOrderExecutionTriggerScheduler`)
  (`docs/agent-tasks/claude-broker-order-execution-trigger-scheduler-20260917.md`).
  ACTIVE grant를 순회해 연결된 포트폴리오의 Track A 보유 종목 중 SELL 신호이고
  grant의 `strategyId`와 일치하며 USD 통화인 종목만 `BrokerOrderExecutionService`
  로 넘긴다. 매수(신규 진입)는 포지션 사이징 설계가 필요해 범위 밖으로 명시적
  분리. 기본값 비활성(`PremarketGuideScheduler`와 동일 관례). 전체 테스트
  스위트(1077개) 통과 확인.
- 오토매매 매수(BUY, 신규 진입) 트리거 추가(같은
  `BrokerOrderExecutionTriggerScheduler`,
  `docs/agent-tasks/claude-broker-order-execution-buy-trigger-20260918.md`).
  포지션 사이징을 새로 만들지 않고 기존 `TradePlanPreviewService`(위험 한도·
  손절가 기반 계산)의 후보 매수 수량·금액을 그대로 재사용. `TradePlanPreviewStatus
  .BUY`이고 전략 일치·USD 통화인 후보만 실행. **가용 현금 잔고 사전 확인은**
  **하지 않음**(`CASH_BALANCE` 어댑터 미구현) - 자금 부족은 향후 실제 브로커의
  주문 거부(`FAILED`)로 걸러지는 것을 최종 방어선으로 삼기로 사용자 승인
  (2026-09-18). 전체 테스트 스위트(1081개) 통과 확인.
- 실제 Toss 주문 제출 어댑터(`TossOrderSubmissionProvider`)
  (`docs/agent-tasks/claude-toss-order-submission-provider-20260918.md`).
  공식 OpenAPI 스펙(`POST /api/v1/orders`, 2026-09-18 직접 조회로 확정)에
  맞춰 시장가·수량 기반 주문만 구현. `confirmHighValueOrder`는 항상 기본값
  (자동 확인 안 함), 실패는 전부 `BrokerOrderSubmissionResult.failed`로
  수렴(예외로 새지 않음). `BrokerProvider.TOSS_SECURITIES`에 `ORDER_SUBMISSION`
  capability 선언 추가 - **이걸로 SELL·BUY 트리거가 이론상 실제로 주문을**
  **낼 수 있는 상태가 됐다.** 다만 실거래는 사용자가 (1) 앱에서 Toss 계정을
  직접 연결하고 (2) 오토매매 opt-in 동의를 직접 생성하고 (3)
  `live-enabled=true`를 설정해야만 발생한다 - 이 세션은 셋 중 어느 것도
  하지 않았고 사용자의 실제 자격증명을 전혀 다루지 않았다. 전체 테스트
  스위트(1088개) 통과 확인.

### 진행 중

등록된 `TRACK_A` 프로필을 후보 유니버스로 사용하고, 포트폴리오의 보유 종목을 제외한 후보에 `BUY`/`WATCH` 판단을 제공한다. 보유·후보 다종목 조회는 완료 주봉 캐시를 사용하고, 시장 데이터가 없는 종목은 전체 실패 대신 `unavailableAssets`에 기록한다. 429 요청 제한이 발생하면 남은 외부 요청을 중단하고, 호출하지 않은 종목도 요청 제한 메시지로 기록한다. 현재 후보 API는 S&P 500 전체나 `TRACK_B`를 스크리닝하지 않는다.

증권사 연동은 연결 확인, 계좌 선택, 보유 종목 스냅샷과 주문 이력 가져오기까지 읽기 전용으로 동작한다. 주문 이력은 자동으로 원장에 쓰지 않고, 정합성 대조와 명시적 승인 이후에만 `BROKER_ORDER_HISTORY` 출처로 반영한다. 토스 공식 계약 대조 결과 `GET /api/v1/orders`와 `orderId`, `filledQuantity`, `filledAt`, 커서 페이지네이션을 기준으로 구현되어 있으며, 주문 전송은 제공하지 않는다. 현재 주문 이력 원장 반영은 미국 시장만 지원한다.

기능 구현은 최신 `main`에서 새 브랜치를 만들고 PR로 병합한다. Claude Code는 별도 리서치 브랜치·동일 worktree에서 `research/**`만 수정하고, Codex와 사용자가 개발 브랜치에서 정책을 채택·구현한다.

`PortfolioRiskPolicy`는 `Portfolio`에 포함되는 `@Embeddable` 값 객체로 저장되며, 위험 비율은 소수점 여섯 자리까지 보존한다. 선택적인 사용자 설정 손절 기준 비율도 같은 정책에 저장하며, 자동 전략 규칙이 아니라 검토용 가이드 계산에만 사용한다. 포트폴리오별 위험 한도는 다음 API로 설정·조회할 수 있다.

- `PUT /api/members/{memberId}/portfolios/{portfolioId}/risk-policy`
- `GET /api/members/{memberId}/portfolios/{portfolioId}/risk-policy`

요청 DTO는 각 비율을 `0 초과, 1 이하`로 검증하고, `주문당 최대 손실 비율 <= 종목당 최대 노출 비율` 관계 규칙은 도메인 객체가 검증한다. 정책이 설정되지 않은 포트폴리오를 조회하면 `404 Not Found`를 반환한다.

`GET /api/members/{memberId}/portfolios/{portfolioId}/risk-alerts`는 실제 종목별 노출 비중이 정책의 종목별 최대 노출 비율을 초과한 경우에만 경고 목록을 반환한다. 이 기능은 자동 매도, 손절가 산출, 주문 비율 산출을 수행하지 않는다.

프론트엔드에는 React + TypeScript + Vite를 초기 구성했고, Vite 개발 프록시(`/api` -> `http://localhost:8080`)로 Spring Boot와 연결한다. 포트폴리오 위험 경고 화면은 목록·빈 상태·오류 상태를 표시하며, 백엔드 오류 응답의 `message`를 화면에 표시한다. `↻` 버튼은 브라우저 전체 새로고침 없이 같은 위험 경고 API를 다시 조회하며, 조회 중에는 중복 클릭을 막는다. 이 화면의 정적 UI, API 연동, 오류 메시지 개선, 재조회 기능은 각각 커밋되어 있다.

위험 경고 목록은 `App`이 `riskAlerts.map()`으로 반복하고, `RiskAlertItem` 컴포넌트가 경고 객체 한 건을 `alert` prop으로 받아 카드 한 장을 표시하도록 분리했다. `alertCount`는 별도 상태가 아닌 `riskAlerts.length`에서 계산한 값이며, 조회 중에는 `isLoading`으로 건수 위치에 `조회 중`을 표시한다. API 모듈의 공통 오류 메시지 함수는 기능별 기본 문구를 인자로 받아 재사용하도록 정리했다.

위험 한도 편집 기능도 `App`에 연결했다. `getPortfolioRiskPolicy()`의 응답을 상태와 입력값에 저장하고, API의 0~1 비율은 화면에서 퍼센트로 변환해 표시한다. 사용자가 저장하면 범위와 손실 한도/노출 한도 관계를 먼저 검증한 뒤 `updatePortfolioRiskPolicy()`로 `PUT` 요청을 보낸다. 선택적인 손절 기준을 입력하면 저장된 비율을 전략 기준 가격에 적용한 검토용 손절가를 가이드에 표시한다. 저장 성공과 실패 메시지는 분리해 표시하며, 성공 후 위험 경고 목록을 다시 조회해 변경된 한도 기준을 화면에 반영한다.

2026-08-25에 위험 한도 편집 화면과 학습·협업 지침을 각각 `14bc5cf`, `4023e3c`으로 커밋했다. 프론트엔드 `npm run lint`, `npm run build`, 위험 한도 저장과 경고 재조회 수동 확인을 완료했다.

2026-08-26에 포트폴리오 평가 요약 화면을 구현했다. 평가 API의 TypeScript 타입과 API 모듈을 추가하고, 공통 오류 응답 해석을 `apiError.ts`로 분리했다. `App`은 평가금액·평가손익·수익률을 로딩·오류 상태와 함께 표시하며, 금액·퍼센트 포맷과 양수·음수 색상을 적용한다. 포트폴리오 정보 새로고침은 위험 경고, 위험 한도, 포트폴리오 평가를 함께 다시 조회한다. `npm run lint`, `npm run build`, 수동 화면 확인을 완료했다. `675a11f`는 평가 관련 파일 골격만 담은 선행 커밋이며, 실제 구현 변경은 세션 종료 시점에 별도 커밋이 필요하다.

2026-09-03에 매매 기록 등록 화면을 현재 완전 지원되는 미국 시장으로 제한했다. `Market` 도메인은 확장성을 위해 `US`, `KR`을 유지하지만, 한국 시장은 종목 검색·현재가·통화 모델이 준비될 때까지 화면에서 선택하지 않는다. 또한 보유 종목 화면에서 수동 매매 기록을 삭제할 수 있게 했다. 삭제는 포트폴리오 소유권과 거래 기록 소속을 확인하고, 남은 전체 이력을 다시 계산해 이후 매도가 초과 매도가 되는 경우에는 저장소 삭제 전에 차단한다. 서비스·컨트롤러 테스트, 프론트엔드 lint/build, 로컬 브라우저 표시를 확인했다.

같은 날 대시보드의 보유 종목 영역은 평가금액 기준 상위 3개 종목의 평가금액·수익률만 보여 주는 요약으로 바꿨다. 전체 수량·평단가·현재가·손익 표는 보유 종목 화면에서만 제공해, 대시보드는 현재 평가·위험·다음 전략 판단을 빠르게 읽는 역할로 유지한다.

2026-09-14에 장전 가이드와 보유·후보 전략 가이드가 같은 종목의 주봉 데이터를 동시에 요청할 때 외부 시세 호출이 중복될 수 있는 문제를 확인했다. `CompletedWeeklyCandleCache`에 완료 주봉 기준일을 포함한 single-flight 로딩을 적용해 같은 종목·같은 기준 주간의 동시 요청은 하나의 외부 조회 결과를 공유하도록 했다. 로딩 실패는 캐시에 저장하지 않아 다음 요청이 다시 시도할 수 있으며, 동시 요청 병합 테스트와 백엔드 전체 테스트, 프론트엔드 lint/build를 통과했다. MACD는 별도 리서치에서 운영 채택 게이트를 통과하지 못했으므로 이번 변경에도 운영 엔진으로 추가하지 않았다.

장전 가이드 운영 문서(`docs/PREMARKET_GUIDE_OPERATIONS.md`)를 추가하고, 수동 생성·같은 날 스냅샷 재사용·`force=true` 강제 재생성·포트폴리오별 스케줄러 오류 격리를 테스트했다. 프리마켓 화면도 시장 데이터 429 응답 시 기존 저장 결과를 유지하면서 공통 재시도 대기 UI를 표시하도록 맞췄다. 자동 스케줄러는 외부 호출량을 확인하기 전까지 기본 비활성으로 유지하며, 운영 환경 변수로 명시적으로 켠다.

2026-09-16에 장전 가이드, 종목별 손절 기준 재정의, 매매 계획 초안(trade-plan-preview) 기능을 5개 기능 단위 커밋으로 정리해 반영했다(`b145110`, `c3a3743`, `8cf4c82`, `579d0e1`, `660fd36`). Claude는 Git 경계 정책상 커밋·푸시를 직접 실행할 수 없어(권한 게이트로 거부 확인) 사용자가 직접 커밋·푸시를 수행했다.

같은 날 "Toss 계좌 동기화 후 보유 수량 차이 정합성 처리"의 남은 절반을 확인·구현했다. 기존에는 `PortfolioBrokerHoldingAdjustment`가 증권사 수량이 원장보다 많은 경우(조정 매수)만 지원했고, 원장이 더 많은 경우(예: 실제로는 매도됐지만 Trade Guide에는 기록되지 않은 경우)는 422로 거부하며 "수동으로 정리하라"는 안내만 있었다. `PortfolioBrokerHoldingAdjustmentWriter`에 반대 방향(조정 매도) 처리를 추가했다: 실제 체결가를 알 수 없으므로 단가를 원장의 조정 전 평균 매입가와 동일하게 두어 실현손익을 0으로 만들고 평균 매입가를 그대로 유지한다. 두 수량이 이미 같은 경우만 여전히 422로 거부한다. API 계약(`POST/GET/DELETE .../broker-holding-adjustments`)은 변경하지 않았고, 방향은 서버가 자동 판정한다. `PortfolioBrokerHoldingAdjustmentWriterTest`를 갱신해 기존 두 개의 거부 테스트 중 하나를 "이미 수량이 같은 경우" 거부로 남기고, 다른 하나를 신규 조정 매도 생성 테스트로 교체했다. 백엔드 전체 테스트 1,017건 통과.

프론트엔드 화면(`BrokerHoldingSnapshotSection.tsx`) 변경은 Antigravity 담당이라 작업 계약을 `docs/agent-tasks/antigravity-broker-holding-adjustment-sell-direction-20260916.md`에 남겼다. Antigravity가 이 세션 중에 완료했다: 음수 불일치(`isNegativeMismatch`)에도 양수 불일치와 동일한 확인 흐름(`openAdjustmentConfirm`/`confirmAdjustment`)을 연결해 "초과분 매도 조정 반영" 버튼을 노출하고, 백엔드가 방향을 자동 판정하는 단일 엔드포인트(`approveBrokerHoldingAdjustment`)를 그대로 재사용했다. 확인 다이얼로그에는 "원장 평균 매입가 적용(실현손익 0)" 안내를 추가했고, 조정 이력 목록도 `brokerQuantity < ledgerQuantityBefore` 기준으로 매수/매도 조정 pill을 구분해 표시한다. `c573556` 커밋으로 반영·검증 완료.

같은 날, 사용자가 매매 계획 초안에서 심각한 버그를 보고했다: SOXL 현재 주가가 102인데 손절 가이드는 113으로 표시되는 등, 실시간 주가와 무관한 손절 판정이 나왔다. `TradePlanPreviewService`를 확인한 결과 두 가지 문제가 있었다 - (1) 손절 판정 기준이 실시간 현재가가 아니라 최근 완료 주봉 종가(`referencePrice`, 최대 1주 전 가격)였고, (2) 더 심각하게는 주간 추세가 `HOLD`인 종목은 손절 판정 자체를 건너뛰는 구조였다(실제 주가가 이미 손절가 밑이어도 주간 추세만 `HOLD`면 손절 검토가 아예 안 나타남). `PortfolioValuationService`가 이미 계산해 두는 `HoldingValuation.currentPrice`(`PortfolioRiskPolicy.stopLossRatio` 설정 시에만 조회)를 손절 판정에 쓰도록 고치고, 이 판정을 `BUY`/`HOLD` 분기보다 먼저 실행해 주간 추세와 무관하게 안전 검토로 최우선 표시하도록 재구성했다. `TradePlanPreviewServiceTest`에 `prioritizesStopLossExitReviewOverHoldTrendWhenCurrentPriceHasAlreadyBreachedStopLoss` 테스트를 추가했고, 브라우저로 SOXL이 실제로 "손절 검토"로 바뀌는 것을 확인했다.

같은 날 사용자가 "전략가이드에서 TQQQ는 티커만 나오고 있다"는 문제를 보고했다. `AssetDisplayNameResolver`(신규, `AssetListingRepository.findByMarketAndTicker` 조회, 없으면 티커로 대체)를 만들어 전략 가이드·매매 계획 초안·장전 가이드 응답(`AssetStrategyGuideResponse`, `AssetTradePlanPreviewResponse`, 배치 응답들, `PremarketGuideResponse`)에 `displayName`을 추가했다. Antigravity가 프론트엔드에서 종목명·시장 배지·티커를 그룹화해 표시하도록 맞췄고, 아코디언 UI 정리(밀도 감소, 제목-배지 간격 버그 수정)와 매매 기록 화면의 `BROKER_HOLDING_ADJUSTMENT` 출처 표시(삭제 버튼 대신 `/broker-accounts` 링크)도 같은 흐름에서 완료했다.

이후 사용자 질문("시장 상황이나 추세 트레이딩 기법 없이 고정 손절만 가이드하는 게 맞나?")을 계기로 Claude 리서치 모드에서 Track A/B 전략 리서치를 대규모로 진행했다. **Track A**: 시장국면필터(SPY 40주선)를 TNA/FAS로 확장 검증(49사이클)했으나 필터가 구조적으로 발동하지 않음을 재확인했고, Chandelier Exit(추적 손절 + 매일 재계산 ATR, k=2/3)도 표준 배수가 3배 레버리지 ETF엔 너무 좁아(평균 손절폭 8.3%, 발동률 98%) 검증한 후보 중 가장 파괴적이었다 - 고정비율·ATR·추적손절·Chandelier Exit 4개 접근 모두 기각. **Track B**: 카탈로그 후보 4종(Track A 규칙 재사용, 횡단면 모멘텀, PEG/PER 밸류에이션, 저변동성+퀄리티 팩터)과 추가 가설 3종(MACD, 단기 반전, 배당성장)까지 8개 후보를 S&P100 101종목 실데이터로 워크포워드 검증했고 전부 기각됐다. 흥미로운 후속 진단(`research/reports/track-b-market-concentration-diagnosis.md`)은 이 반복된 기각이 전략 결함이 아니라 2015~2026 표본이 소수 메가캡(NVDA/AMD/AVGO 등)에 의해 비대칭적으로 견인된 국면 때문임을 실증했다(벤치마크에서 상위 5종목만 제외해도 초과수익 격차가 68~100% 줄어듦, 1개 구간은 부호 반전). 이 진단은 재채택 근거가 아니며, 사용자와 합의해 Track B는 당분간 규칙 기반 자동 매매 확장을 중단하고 지금의 후보 발굴 스크리닝 수준을 유지하기로 했다. 이 과정에서 (a) Finnhub 무료 API가 실제로는 시점별 재무 시계열을 제공한다는 것을 문서 추정이 아니라 실제 호출로 검증해 최초 판단을 정정했고, (b) `momentum_engine.py`의 매도 레그 거래비용이 실제 현금에 반영되지 않는 회계 버그를 발견해 신규 스크립트에서 수정했다(기존 3개 리포트는 회전율이 낮아 결론에 영향 없음, caveats로 기록). 전체 경과·리포트 목록은 `research/TASKS.md` 6~7절, 원자료는 `research/data/backtests.json`을 참고한다.

2026-09-18에 오토매매(자동 주문) 파이프라인을 opt-in 동의 → 킬스위치 → 드라이런 오케스트레이션 → SELL/BUY 트리거 → 실제 Toss 주문 어댑터(`TossOrderSubmissionProvider`) 순으로 완성했다. `BrokerProvider.TOSS_SECURITIES`에 `ORDER_SUBMISSION` capability를 선언하고 어댑터를 등록했지만, 실거래는 여전히 (1) `tradeguide.broker.order-execution.live-enabled=true`와 (2) 사용자가 직접 Toss 계정 연결 후 opt-in 동의(grant) 생성, 두 가지를 모두 사용자가 앱 화면에서 직접 해야만 발생한다 - 이 세션은 둘 다 켜지 않았다. 백엔드 전체 테스트 1,088건 통과.

같은 날, 사용자가 증권사 연결 폼의 Client Secret 필드 UI를 지적했다: "보기"/"숨김" 텍스트 버튼이 관례에 맞지 않고(눈 아이콘이 일반적), 긴 값을 평문으로 표시하면 텍스트 끝이 버튼과 겹쳐 보였다(Claude가 실제 긴 값을 입력해 헤드리스 브라우저로 재현·확인). 이 수정은 `frontend/src/**` 소유권에 따라 Antigravity CLI에 위임했다(`docs/agent-tasks/antigravity-secret-field-icon-and-overlap-fix-20260918.md`) - 사용자가 Orca 오케스트레이션 워커 디스패치 실패 후 Antigravity를 직접 열어 작업 계약을 전달했다. Antigravity가 `BrokerConnectionSection.tsx`의 `renderCredentialFieldInput`(신규 연결/자격 증명 갱신 폼 공유 함수)을 인라인 SVG 눈/눈-빗금 아이콘으로 교체하고(`aria-label` 유지), `App.css`의 `.secret-input-wrapper input` 패딩을 64px→44px로 줄이고 `text-overflow: ellipsis`를 추가해 겹침을 해결했다. Claude가 `npm run lint`/`npm run build` 및 데스크톱·375px 모바일 폭 양쪽에서 브라우저로 직접 재검증(긴 값 입력 후 겹침 없음, 눈 아이콘 정상 표시)했고, 커밋 `1eddca5`로 반영했다.

2026-09-21에 외부 "무한매수법 V4.0" 방법론을 일반 원리(예산 N분할, 평단 기준 동적 기준선, 1/4 부분 매도, 소진 시 감량)로 재정의해 SOXL/TQQQ/FAS/TNA 일봉으로 검증했다(`research/reports/track-a-infinite-buy-averaging-cycle-validation.md`, 커밋 `b00cc4d`). 3년·5년 롤링 윈도우를 학습(≤2018)/검증(≥2019)으로 나누고 평균 노출과 같은 비중의 단순 (종목+현금) 벤치마크로 통제했다. 결론은 전략 채택 비추천이다: 상승장에서 단순 보유에 크게 뒤지고, 종목 간 결과가 갈리며(검증 구간 SOXL 우세, TQQQ 열세), 낙폭이 작아 보인 것은 평균 노출이 30~40%였기 때문이고 동일 노출 대비 MDD는 오히려 깊었다. 학습에서 고른 설정은 검증에서 붕괴했다. 예산÷분할 수 구조만 N=40에서 4종목 동일 노출 대비 3년 중앙 +8~15%로 일관돼 가격 사다리 설계 후보로 남겼고(H2), 리버스 모드(H3)와 40주선 필터(H4)는 기각, 동적 기준선(H1)은 추가 검증 필요다. 정책 문서는 바꾸지 않았다. 원문 저작권 안내 때문에 원문 규칙·표는 저장소에 옮기지 않았다.

### 2026-09-23 장전 가이드 입력 근거 진행 현황

- Codex의 장전 가이드 입력 근거 1단계 커밋(`e495e61`~`92b87b1`)은 Claude가 리뷰했고 즉시 수정할 결함은 보고되지 않았다. 자산별 완료 주봉 해시와 Toss 페이지별/Twelve Data 단일 응답 수신 시각은 저장되지만 전체 입력은 계속 미검증이다.
- Claude가 Toss 이력 끝 US/KR 6종목을 관측하고 회귀 테스트(`ac4e98f`), 캔들 해시 v2 정규화(`f36f1b6`), V31~V33 PostgreSQL 근거 저장 왕복 테스트(`334711d`), Toss 일봉 세션 범위 1차 관측(`cb2be95`), 국내 커서 관측 문서(`43ff269`)를 반영했다. 보고된 검증은 PostgreSQL 통합 테스트 37개, 백엔드 전체 테스트 1,113개 통과·3개 건너뜀이다.
- 협업 정책은 `d245e86`, `35780e0`, `6681f83`, `5284da4`에서 변경됐다. Antigravity는 전달 경로에서 제외하고 Codex/Claude가 교차 검증한다. 구체적인 소유권과 Git 경계는 최신 `docs/AI_COLLABORATION_POLICY.md`를 우선한다.
- 다음 관측: 2026-09-24 05:00 KST 이후 Toss 일봉 2차 관측으로 최신 일봉의 확정 시점 가설을 검토한다. 관측 전에는 가격 보정 규칙을 확정하지 않는다.
- Codex 판단 대기: 복구본 `outputs/recovered-research` 및 설계·프로토콜 문서의 반영 방식, 정책 문서의 자동 주문 표현 불일치 정리 담당, 포트폴리오 상태 스냅샷(C-2) 우선순위. 복구본 테스트 124개·48개 통과는 Claude 보고이며, 복구본을 현재 저장소에 반영했다는 뜻은 아니다. `session-engine-data-preflight-v1`, `session-plan-core-v1` 작업 계약도 아직 저장소에 없다.

### 다음 작업

1. **장전 가이드 전체 입력 계보의 다음 단위를 결정한다** - 자산별 캔들 근거는 완료됐으나 포트폴리오 상태·위험 설정·후보군을 같은 시점에 고정하지 못했다. C-2 포트폴리오 상태 스냅샷을 우선 검토하고, 전체 입력 해시·검증 상태 상향은 필요한 근거가 모인 뒤에만 진행한다. 이전의 누적 미커밋 기능 목록은 현재 HEAD 상태를 나타내지 않으므로, 새 작업 전 `git status`로 실제 변경을 확인한다.
2. Track B는 규칙 기반 자동 매매 확장을 중단하기로 했으므로, `StrategySelector.select(TRACK_B)`에 `TradingStrategy` 구현체를 추가하는 작업은 하지 않는다. 현재의 PEG<1 && 50일선 위 스크리닝(`research/data/candidates.json`)을 자동화할지(Finnhub 무료 API로 가능해짐, `research/reports/track-b-fundamental-data-provider-evaluation.md`)는 별도 결정 필요.
3. 손절 없는 검토용 주문 초안은 허용하되, `stopLossPrice`가 없으면 증권사 전송 준비 상태가 되지 않도록 분리했다. Track A의 자동 손절 규칙은 채택하지 않는다(고정비율/ATR/추적손절/Chandelier Exit 4개 후보 전부 기각 확정, `research/TASKS.md` 6절). 기본값은 `NOT_CONFIGURED`로 유지하고, 사용자가 직접 입력한 손절 기준 비율이 있을 때만 검토용 손절가를 계산한다.
4. ~~토스 주문 이력의 국내 시장 원장 반영~~ - 2026-09-16 완료. 설계 계약
   (`docs/agent-tasks/toss-krw-order-history-ledger-design-20260916.md`)에서 "환율
   변환 없이 통화별 별도 표시"로 정책을 정하고 구현했다: `Currency` enum(USD/KRW)과
   `Market.getCurrency()` 파생 메서드, `MarketPrice`의 통화 검증(시장-통화 불일치 시
   예외), `TossSecuritiesOrderHistoryProvider`의 KRX 6자리 숫자 종목코드 인식(전량
   제외 → 실제 반영 대상), `PortfolioValuation`/`CurrencyValuationTotals`의 통화별
   집계(`totalsByCurrency`, API 계약 변경 - 프런트엔드 반영은
   `docs/agent-tasks/antigravity-portfolio-valuation-currency-breakdown-20260916.md`
   로 넘김)까지 마쳤다. `PortfolioValuationService.fetchPrices`가 이미 시장별로
   묶어 배치 조회하는 구조라 KR 시세 조회는 추가 배관 없이 그대로 동작함을 통합
   테스트(`fetchesPricesSeparatelyPerMarketWhenHoldingsSpanUsAndKr`)로 확인했다.
   **의도적으로 범위 밖에 둔 것**: `PortfolioRiskPolicy`·`trade-plan-preview`의 위험
   한도·매매 계획 계산은 USD만 대상으로 유지하고 KRW 보유 종목은 제외한다(사용자
   승인, 통화 혼합 위험 회피) - Track A/B 전략 가이드도 여전히 US 전용이다. 국내
   주식 전용 전략(Track C 격)이 필요한지는 별도 검토 대상으로 남겨뒀다 - 기존 Track
   A/B 리서치는 전부 미국 주식 기준이라 그대로 재사용할 수 없다. KR 종목 검색(신규
   상장 종목 자동 표시명 채우기)도 이번에는 하지 않았고, 미등록 종목은 티커를 그대로
   표시명으로 쓴다.
5. 토스의 장기 조회 범위(U-4)와 WTS 개인 이용약관(U-15)은 공식 기술 문서만으로 확정되지 않았다. 실제 서비스 공개 전 운영자 확인이 필요하며, 이를 코드가 확인된 것처럼 처리하지 않는다.
6. 시장 데이터 제공자는 개발 환경의 요청 제한을 완화할 방법과 운영용 Twelve Data/Yahoo 사용 정책을 별도 결정한다. 공급자를 바꾸더라도 전략 기준 데이터와 조정 종가 규칙을 섞지 않는다.
7. `trade-plan-preview`는 위험 한도와 사용자 손절가를 이용해 검토용 최대 매수 금액·수량과 예상 최대 손실을 계산한다. 이는 저장·자동 주문·증권사 전송 기능이 아니며, 가용 현금은 미연동 제약으로 표시한다. 일반 매도에는 임의 수량을 만들지 않고, 손절 기준 도달 때만 전량 손절 검토를 표시한다.

## 새 대화 시작용 인계 문구

```text
trade-guide 프로젝트 학습을 이어서 진행한다.
저장소의 AGENTS.md, docs/PROJECT_CONTEXT.md, docs/LEARNING_LOG.md를 먼저 읽고 현재 상태를 파악한다.
현재 브랜치와 git status를 확인한 뒤, 백엔드 전체 테스트와 `frontend`의 `npm run lint`, `npm run build`를 실행해 현재 변경을 먼저 확인한다. 주문 이력·개시 잔고·보유 스냅샷 기능은 이미 구현된 것으로 간주하고 같은 기능을 다시 만들지 않는다. 다음 작업은 `docs/PROJECT_CONTEXT.md`, `docs/ARCHITECTURE_ROADMAP.md`, `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`, `docs/agent-tasks/broker-transaction-history-api-verification.md`의 현재 범위와 미확정 사항을 기준으로 선택한다. Toss 주문 이력은 미국 원장 반영까지만 완료되었고, 국내 시장·장기 조회 범위·WTS 약관·자동 주문은 별도 승인 전까지 구현하지 않는다.
Claude 리서치가 필요하면 SETUP.md의 동일 worktree 원칙을 따르고, 리서치 결과를 정책·테스트·구현으로 옮기기 전 명시적으로 검토한다.
다음 구현을 제안하기 전에 관련 enum, 도메인 객체, 서비스와 테스트를 직접 읽어 현재 계약을 확인한다.
나는 직접 구현하므로 한 단계씩 구현 가이드를 제공하고, 내가 완료했다고 말한 뒤에 다음 단계를 안내한다.
```
