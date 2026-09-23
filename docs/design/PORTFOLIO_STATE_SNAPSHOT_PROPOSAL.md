# 장전 가이드 포트폴리오 상태 스냅샷(C-2) 제안

상태: **제안 (사용자 수락 전)**. 2026-09-23 Claude가 `fe98f2e` 기준 코드를 읽기 전용으로 대조해 작성했다. 수락 전에는 정책이나 구현으로 취급하지 않는다. 테스트를 새로 실행하지 않았고, 아래 동시성 위험은 코드 구조로 추론한 것이며 재현하지 않았다.

관련 문서: `docs/design/GUIDE_INPUT_OBSERVATION.md`(자산별 캔들 근거 1단계, 현재 상태).

## 1. 목적과 범위

장전 가이드 한 건이 **어떤 포트폴리오 상태를 보고 만들어졌는지**를 같은 시점의 값으로 고정해 기록한다. 자산별 캔들 근거(1단계)와 합쳐 이후 전체 입력 해시로 가는 선행 단위다.

이 제안은 전체 입력 해시(`inputSha256`)를 채우거나 감사 상태를 `CAPTURED`/`VERIFIED`로 올리지 않는다. 제공자 가격 조정 정책 계보와 Toss 일봉 세션 범위 2차 관측(2026-09-24 05:00 KST 이후) 결론이 아직 없다.

## 2. 장전 가이드가 읽는 포트폴리오 입력

`PremarketGuideService.generateToday` → `PortfolioStrategyGuideService`·`PortfolioCandidateStrategyGuideService` 경로 기준이다.

| 입력 | 코드상 출처 | 가이드에서의 용도 | 현재 기록 |
|---|---|---|---|
| 보유 종목(수량·평단) | `HoldingService` ← `TradeTransaction` 전체 | 보유 가이드, 후보에서 보유 제외 | 없음 |
| 보유 종목 트랙 재정의 | `PortfolioAssetStrategyProfile` | 보유 신호의 트랙 | 없음 |
| 전역 트랙 카탈로그 | `AssetProfile` (포트폴리오 밖 전역 입력) | 재정의 없는 보유 종목의 트랙, 후보 대체 목록 | 없음 |
| 후보군 | `PortfolioCandidateAsset`, 없으면 전역 `TRACK_A` 카탈로그 | 후보 가이드 | 없음 (어느 경로였는지도 없음) |
| 손절 비율 | `Portfolio.riskPolicy.stopLossRatio` + `PortfolioAssetRiskOverride` | 손절 판단 | 없음 |
| 캔들 제공자 | `Portfolio.marketDataPreference` | 시세 조회 | 있음 (`snapshot.marketDataProvider`) |
| 최신 증권사 보유 스냅샷 | `PortfolioBrokerHoldingSnapshot` | 보유 0건일 때 안내 문구만 | 없음 |

장전 가이드는 수량을 산출하지 않으므로 현금, 예약 주문, `maxLossPerTradeRatio`, `maxSingleAssetExposureRatio`는 입력이 아니다. C-2에서는 "누락"이 아니라 "해당 없음"이며, 매매 계획 초안·예측엔진 범위에서 따로 다룬다.

## 3. 코드 대조로 확인한 원자성 문제

1. **같은 원장을 두 번 읽는다.** 보유 배치와 후보 배치가 각각 `holdingService.getHoldings`를 호출한다. 재정의·손절 설정·후보 목록도 개별 쿼리이며, 그 사이에 종목별 외부 시세 호출이 끼어 첫 조회와 마지막 조회가 수 초~수 분 벌어질 수 있다.
2. **단일 스냅샷이 보장되지 않는다.** `generateToday`는 격리 수준 지정 없는 `@Transactional`이며 PostgreSQL 기본값 READ COMMITTED에서는 쿼리마다 최신 커밋을 본다. 생성 중 원장 변경(예: 증권사 주문 가져오기 승인)이 커밋되면 한 가이드 안에서 같은 종목이 보유와 후보에 동시에 나오거나 둘 다 빠질 수 있다. **추론이며 재현하지 않았다.**
3. **리비전 번호 도입이 어렵다.** 저장소에 `@Version`이 없고 `TradeTransaction`은 삭제된다. 원장 쓰기 지점이 7개 클래스 8곳(수동 등록·삭제, 주문 가져오기 승인, 보유 반영 생성·삭제, 개시 잔고, 보유 조정 생성·삭제)이라 리비전 카운터는 모든 쓰기 경로 수정이 필요하다.

## 4. 권장 방식: 한 번 읽기 → 불변 값 객체 → 저장 직전 재대조

1. **한 번 읽기.** `generateToday` 시작 시 새 리더가 2절의 입력을 한 번에 읽어 불변 값 객체(가칭 `PortfolioDecisionInputs`)를 만든다. 두 배치 서비스는 원장을 다시 조회하지 않고 이 객체를 받는다. 기존 메서드는 전략 가이드 API가 쓰므로 유지하고 오버로드를 추가한다.
2. **읽기 일관성.** 이 읽기만 짧은 읽기 전용 `REPEATABLE READ` 트랜잭션(별도 빈, `REQUIRES_NEW`)으로 수행해 여러 쿼리가 같은 DB 스냅샷을 보게 한다. 외부 시세 호출 전에 끝난다.
3. **저장 직전 재대조.** 저장 직전 같은 리더로 다이제스트를 다시 계산한다. 다르면 재시도하지 않고(외부 재호출·429 위험) 가이드를 저장하되 `PORTFOLIO_STATE_CHANGED_DURING_GENERATION` 사유를 남긴다.
4. **저장.** 새 테이블(V34) `premarket_guide_portfolio_states`를 감사 테이블과 같은 `@MapsId` 1:1(`snapshot_id` 기본키 겸 외래키)로 둔다.

   | 컬럼 | 의미 |
   |---|---|
   | `state_schema_version`, `read_at`(Instant) | 형식 버전, 읽은 시각 |
   | `ledger_sha256`, `ledger_transaction_count` | 원장 다이제스트와 건수 |
   | `holdings_sha256` | 계산된 보유 종목 다이제스트 |
   | `strategy_overrides_sha256`, `risk_settings_sha256` | 트랙 재정의, 손절 설정 |
   | `candidate_source`(`PORTFOLIO`/`GLOBAL_CATALOG`), `candidate_set_sha256` | 후보군 출처와 다이제스트 |
   | `asset_catalog_sha256` | 전역 카탈로그(전역 입력임을 명시) |
   | `broker_snapshot_id` (nullable) | 참조한 증권사 보유 스냅샷 |
   | `state_sha256` | 구성요소 결합 다이제스트 |

   스냅샷 저장과 같은 트랜잭션에서 cascade로 저장해 결과·캔들 근거·포트폴리오 상태가 함께 커밋되게 한다. 강제 재생성은 같은 행을 갱신한다. V31부터 비어 있는 `premarket_guide_input_audits.portfolio_state_ref`에는 일관된 캡처일 때만 `state_sha256` 참조를 넣는다.
5. **정규화.** 내용 필드만 포함하고 `createdAt`/`updatedAt`은 제외해 같은 값의 재저장이 다이제스트를 바꾸지 않게 한다. 안정된 키로 정렬하고 금액은 캔들 해시 v2와 같은 `stripTrailingZeros` 규칙을 쓴다. 원장에는 거래 id를 포함해 삭제 후 같은 내용 재등록도 변경으로 본다.
6. **개인정보.** 다이제스트와 건수만 저장하고 수량·평단 원문은 저장하지 않는다. 대신 거래가 삭제되면 과거 상태를 재구성할 수 없고 검증만 가능하다(D1).

## 5. 감사 상태 규칙

| 상황 | 누락 사유 | `portfolio_state_ref` | 상태 |
|---|---|---|---|
| 일관된 캡처 | `PORTFOLIO_STATE_NOT_CAPTURED` 제거 | `state_sha256` | `UNVERIFIED` 유지 |
| 생성 중 변경 감지 | `PORTFOLIO_STATE_CHANGED_DURING_GENERATION` 추가 | 비움 | `UNVERIFIED` |
| 공통 | `INPUT_DIGEST_NOT_CAPTURED` 유지 | — | — |

`inputSha256`은 계속 비운다.

## 6. 결정 필요 사항

| ID | 결정 | 권장 |
|---|---|---|
| D1 | 다이제스트만 저장 vs 정규화한 상태 원문까지 저장(재생 가능, 개인 보유 정보 중복) | v1은 다이제스트만 |
| D2 | `REPEATABLE READ` 읽기 트랜잭션 도입 vs READ COMMITTED 유지 + 재대조만 | 둘 다. 재대조만으로는 3절 2번 모순을 막지 못하고 감지만 한다 |
| D3 | 전역 `AssetProfile` 카탈로그 포함 여부 | 포함. 트랙·후보에 실제 영향 |
| D4 | 구현 담당과 V34 승인 | 스키마 변경이므로 사용자 승인 후 작업 계약 |

## 7. 영향과 테스트 계획

- 외부 API 계약과 프론트엔드는 바뀌지 않는다. 두 배치 서비스에 오버로드가 추가된다.
- 다이제스트 정규화 단위 테스트(재저장 무변화, 금액 표기 무관, 삭제·재등록 감지).
- 서비스 테스트: 두 배치가 같은 상태 객체를 받는지, 변경 주입 시 사유 기록, 강제 재생성, 회원·포트폴리오 격리.
- PostgreSQL 통합 테스트: V34 왕복, 두 커넥션으로 생성 중 원장 커밋을 일으켜 3절 2번 위험을 먼저 재현하고 `REPEATABLE READ`로 해소되는지 확인. H2는 격리 동작이 달라 대체할 수 없다.

## 부록 A. 복구본(`outputs/recovered-research`) 반영 방식 (미결정)

- 현재: 저장소 밖 로컬 Git(`2f1b1d1`~`765979e`, 커밋 7개). `RECOVERY.md`, `research/` 아래 보고서 6개·스크립트와 예시 17개 추적. 미커밋 설계 문서 1개(`service-guide-evidence-integration-design-2026-09-23.md`). 별도로 `outputs/`에 설계·프로토콜 문서 3개. 2026-09-23 Claude 재실행 결과 테스트 124개·48개 통과.
- 경로 충돌: 없음(main에 `research/scripts/session-*`와 같은 이름의 보고서 없음).
- 참조 누락: 보고서가 참조하는 `docs/agent-tasks/session-engine-data-preflight-v1.md`, `session-plan-core-v1.md`가 main에 없다.
- 내용 불일치: 미커밋 설계 문서의 테이블명(`guide_input_audits`)·상태값(`INCOMPLETE`)이 실제 구현(`premarket_guide_input_audits`, `UNVERIFIED`)과 다르다.
- 방향 불일치: `research/TASKS.md` §9와 `research/STRATEGY_ENGINE_POLICY.md`는 보류된 LLM 매크로 엔진 경로를 가리키고, 조건부 매매 가이드 엔진 설계는 저장소에 없다.
- 원본과의 바이트 단위 동일성은 확인할 수 없다(`RECOVERY.md`에 기록됨).

| 안 | 방법 | 장점 | 단점 |
|---|---|---|---|
| A | 이력 보존 병합(`--allow-unrelated-histories`) | 커밋 7개 보존 | 루트에 `RECOVERY.md`, 관련 없는 이력 혼입 |
| **B (권장)** | 파일만 단일 커밋으로 가져오기 | 이력 단순, 원격 백업 문제 해결 | 커밋 단위 이력 소실 |
| C | 밖에 두고 원격 백업만 | main 무변경 | 저장소만 읽는 에이전트가 방향을 모름 |

B 세부: 스크립트·보고서는 같은 경로로, `RECOVERY.md`는 `research/reports/recovered-research-RECOVERY.md`로 옮기고 출처 커밋 `765979e`를 명시한다. 설계·프로토콜 문서 3개는 "제안·미채택" 머리말과 함께 `research/reports/`에 둔다. 미커밋 설계 문서는 "구현으로 대체됨"을 표기한다. 빠진 계약 2개는 "사후 기록"으로 작성한다. `research/**` 쓰기에는 리서치 작업 계약이 필요하다.

## 부록 B. 자동 주문 정책 문구 불일치 (미결정)

기준: `CLAUDE.md`(2026-09-17 개정)와 `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md` 14행은 "opt-in 회원에 한해 주문 실행, 미동의 회원은 읽기 전용"으로 이미 맞춰져 있다.

| 위치 | 현재 문구 | 권장 |
|---|---|---|
| `AGENTS.md` 66행 | 자동 주문이나 수익 보장을 제공하지 않는다 | CLAUDE.md 원칙 문장으로 교체. 67행 "미국 주식 중심"도 국내 지원 현황과 불일치 |
| `docs/AGENT_WORKFLOW.md` 36행 | It never executes orders | 기본은 의사결정 지원, 명시적 opt-in 회원에 한해 주문 실행 |
| `research/STRATEGY_ENGINE_POLICY.md` 5행 | 주문을 자동 실행하지 않는다 | opt-in 예외 명시 |
| 같은 문서 13행 | 확정 요구사항 "자동 주문 미실행" | "opt-in 자동 주문(2026-09-17 결정)" |
| 같은 문서 145행 | 자동 주문은 하지 않는다 (v1 확정 범위) | v1 전략 판단 자체는 주문하지 않는다는 의미로 한정 |
| 같은 문서 150행 | 손절 없는 초안은 전송 불가 | 정확함, 유지 |
| `research/TASKS.md` (D) 3단계 | 아직 CLAUDE.md 실제 수정은 안 함 | `229e482`와 2026-09-18 구현 완료로 갱신 |

담당 경계: `AGENTS.md`·`AGENT_WORKFLOW.md`는 `governanceEditApproved` 승인, `research/**`는 리서치 작업 계약, `STRATEGY_ENGINE_POLICY.md` 원칙 문장은 사용자 확인이 필요하다. 새 정책이 아니라 문구 정합이므로 한 커밋으로 묶는 것을 권장한다.
