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

## 현재 구현 상태

### 완료

- 회원, 포트폴리오, 매매 기록, 보유 종목 계산
- 현재가와 포트폴리오 평가 조회
- Twelve Data 일봉·주봉 조회
- Track A 주봉 10/40 이동평균 전략 가이드
- 완료된 주봉만 전략 판단에 반영
- 전략 ID, 버전, 데이터 기준일 응답
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
- Twelve Data SOXL 주봉 스냅샷 기반 Track A 골든 테스트
- 완료 주봉의 신선도 검증과 오래된 데이터의 502 응답
- `144091d refactor: 시장 신호와 포트폴리오 결정 분리` 커밋 및 전체 테스트 통과

### 진행 중

등록된 `TRACK_A` 프로필을 후보 유니버스로 사용하고, 포트폴리오의 보유 종목을 제외한 후보에 `BUY`/`WATCH` 판단을 제공한다. 보유·후보 다종목 조회는 완료 주봉 캐시를 사용하고, 시장 데이터가 없는 종목은 전체 실패 대신 `unavailableAssets`에 기록한다. 429 요청 제한이 발생하면 남은 외부 요청을 중단하고, 호출하지 않은 종목도 요청 제한 메시지로 기록한다. 현재 후보 API는 S&P 500 전체나 `TRACK_B`를 스크리닝하지 않는다.

현재 개발 브랜치는 `feature/candidate-entry-window`이다. Claude Code는 별도 리서치 브랜치·동일 worktree에서 `research/**`만 수정하고, Codex와 사용자가 개발 브랜치에서 정책을 채택·구현한다.

`PortfolioRiskPolicy`는 `Portfolio`에 포함되는 `@Embeddable` 값 객체로 저장되며, 위험 비율은 소수점 여섯 자리까지 보존한다. 현재는 도메인·저장소 테스트만 있고, 위험 한도를 설정하거나 조회하는 서비스와 API는 아직 없다.

### 다음 작업

1. Track A 손절 후보를 더 긴 기간·추가 레버리지 ETF·수수료 및 슬리피지 가정으로 검증한다. 현재 고정 비율 `-25%` 손절은 추가 검증 필요이고 ATR 기반 손절은 채택하지 않는다.
2. `TradePlan.quantityRatio`의 기준은 `QuantityRatioBasis`로 명시했고, `PortfolioRiskPolicy`를 `Portfolio`에 영속화했다. 다음으로 위험 한도를 설정·조회하는 서비스와 API 범위를 설계한다. 이 결정 전에는 `TradePlanGenerator`가 주문 비율을 자동 계산하지 않는다.
3. Twelve Data API 키를 폐기·재발급하고, 로컬 환경 변수로만 설정한다. 비밀값은 추적되지 않아도 대화나 파일에 남기지 않는다.

## 새 대화 시작용 인계 문구

```text
trade-guide 프로젝트 학습을 이어서 진행한다.
저장소의 AGENTS.md, docs/PROJECT_CONTEXT.md, docs/LEARNING_LOG.md를 먼저 읽고 현재 상태를 파악한다.
현재 브랜치와 git status를 확인한 뒤, `PortfolioRiskPolicy`의 설정·조회 서비스와 API를 설계부터 이어서 진행한다.
Claude 리서치가 필요하면 SETUP.md의 동일 worktree 원칙을 따르고, 리서치 결과를 정책·테스트·구현으로 옮기기 전 명시적으로 검토한다.
다음 구현을 제안하기 전에 관련 enum, 도메인 객체, 서비스와 테스트를 직접 읽어 현재 계약을 확인한다.
나는 직접 구현하므로 한 단계씩 구현 가이드를 제공하고, 내가 완료했다고 말한 뒤에 다음 단계를 안내한다.
```
