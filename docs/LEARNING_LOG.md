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

### 진행 중

포트폴리오 보유 종목의 현재 평가금액 비중을 계산하는 노출 비중 기능을 구현 중이다.

- `HoldingExposure` 도메인 객체 작성
- `PortfolioExposureCalculator` 작성 및 단위 테스트 통과
- `PortfolioExposureService` 작성

### 다음 작업

1. `HoldingExposureResponse` DTO 작성
2. `GET /api/members/{memberId}/portfolios/{portfolioId}/exposures` 추가
3. `PortfolioControllerTest`에 노출 비중 API 테스트 및 `PortfolioExposureService` Mock Bean 추가
4. 전체 테스트와 Postman 호출 확인
5. 기능 단위 커밋 및 푸시

## 새 대화 시작용 인계 문구

```text
trade-guide 프로젝트 학습을 이어서 진행한다.
저장소의 AGENTS.md, docs/PROJECT_CONTEXT.md, docs/LEARNING_LOG.md를 먼저 읽고 현재 상태를 파악한다.
현재 브랜치와 git status를 확인한 뒤, 노출 비중 API의 DTO, Controller, Controller 테스트부터 이어서 진행한다.
나는 직접 구현하므로 한 단계씩 구현 가이드를 제공하고, 내가 완료했다고 말한 뒤에 다음 단계를 안내한다.
```
