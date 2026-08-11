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
git switch feature/portfolio-foundation
git pull --ff-only
./gradlew test
```

로컬 변경 사항이 남아 있으면 먼저 커밋하거나 처리한 뒤 `pull`한다.

## 현재 구현 상태

### 완료

- 회원, 포트폴리오, 매매 기록, 보유 종목 계산
- 현재가와 포트폴리오 평가 조회
- Twelve Data 일봉·주봉 조회
- Track A 주봉 10/40 이동평균 전략 가이드
- 완료된 주봉만 전략 판단에 반영
- 전략 ID, 버전, 데이터 기준일 응답
- 추세 상태와 교차 이벤트를 함께 반환하는 Track A 전략 판단
- 보유 종목 여부를 반영한 포트폴리오 전략 가이드
- 포트폴리오 보유 종목별 평가금액 노출 비중 계산 및 조회 API
- Twelve Data SOXL 주봉 스냅샷 기반 Track A 골든 테스트
- 완료 주봉의 신선도 검증과 오래된 데이터의 502 응답

### 진행 중

미보유 종목의 Track A 행동과 교차 이후 추격 매수 방지 규칙을 설계한다. 현재 전략 API는 시장 데이터 단독 가이드와 보유 종목 가이드를 구분한다.

### 다음 작업

1. 미보유 종목의 Track A 행동과 교차 이후 추격 매수 방지 규칙을 결정 테이블로 정리한다.
2. 다종목 전략 가이드의 캐시와 일부 종목 조회 실패 응답 형식을 설계한다.
3. `TradePlan`의 주문 가격·수량·유효 기간과 위험 정책을 전략 판단에서 분리해 모델링한다.

## 새 대화 시작용 인계 문구

```text
trade-guide 프로젝트 학습을 이어서 진행한다.
저장소의 AGENTS.md, docs/PROJECT_CONTEXT.md, docs/LEARNING_LOG.md를 먼저 읽고 현재 상태를 파악한다.
현재 브랜치와 git status를 확인한 뒤, 미보유 종목의 Track A 행동과 교차 이후 추격 매수 방지 규칙을 결정 테이블로 설계부터 이어서 진행한다.
나는 직접 구현하므로 한 단계씩 구현 가이드를 제공하고, 내가 완료했다고 말한 뒤에 다음 단계를 안내한다.
```
