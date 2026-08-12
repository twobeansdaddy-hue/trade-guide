# Trade Guide 개발 브랜치 협업 지침

이 저장소는 Java 21 / Spring Boot / JPA / Gradle 기반의 미국 주식 투자 가이드 학습 프로젝트다.
자동 주문 서비스가 아니라, 사용자가 직접 검토할 수 있는 시장 신호와 포트폴리오 가이드를 제공한다.

## 작업 시작

다음 파일과 상태를 먼저 확인한다.

1. `AGENTS.md`
2. `docs/PROJECT_CONTEXT.md`
3. `docs/LEARNING_LOG.md`
4. `research/STRATEGY_ENGINE_POLICY.md`
5. 현재 브랜치와 `git status`

## 학습 방식

- 사용자는 직접 핵심 기능을 구현한다. 한 번에 큰 리팩터링이나 완성 코드를 제시하지 않는다.
- 구현 전에는 관련 소스와 테스트를 읽어 현재 타입, enum 값, API 계약을 확인한다.
- 사용자가 `완료`라고 말하면 변경 내용과 테스트 결과를 확인한 뒤 다음 단계로 진행한다.
- 파일을 만들거나 수정하도록 안내할 때는 반드시 정확한 파일 경로와 역할을 함께 제시한다.

## 현재 전략 계약

- `StrategySignal`: 시장 데이터만으로 계산한 추세, 교차 이벤트, 기준 가격, 데이터 기준일, 교차 후 경과 주
- `StrategyDecision`: 보유 여부 같은 사용자 맥락과 `StrategySignal`을 결합한 최종 행동
- 단독 종목 전략 API는 `StrategySignal`을 반환한다.
- 포트폴리오 전략 API는 보유 종목에 대해 `StrategyDecision`을 반환한다.
- `StrategyAction`에는 `BUY`, `HOLD`, `REDUCE`, `SELL`, `WATCH`가 이미 있다. 현재 보유 종목 경로에서는 `HOLD`, `SELL`만 사용한다.
- `/api/trade-guide/calculate`은 초기 학습용 계산 API다. 사용자가 입력한 수익률·손실률을 계산할 뿐 현재 전략 엔진과 연결되지 않는다.

## 리서치 경계

- `research/`는 별도 리서치 워크플로우의 소유 영역이다. 개발 작업 중 수정하지 않는다.
- 리서치 문서의 구현 현황 설명이 현재 코드보다 오래됐을 수 있다. 구현 계약은 현재 소스와 `docs/PROJECT_CONTEXT.md`를 우선한다.

## 보안

- API 키와 토큰은 환경 변수로만 주입한다.
- 인증 전 `/api/admin/**`과 `memberId` 기반 경로는 로컬 학습용이다. 공개 환경에 그대로 배포하지 않는다.
