# Architecture Roadmap

웹 MVP 이후의 구조 전환 계획이다. 아래 항목은 구현 확정이 아니라, 인증·데이터 모델·외부 연동처럼 구현 전에 별도 승인과 설계 검토가 필요한 순서다.

## 1. PostgreSQL과 Flyway

- 로컬 H2 학습 환경과 운영 PostgreSQL 프로필을 분리한다.
- 현재 JPA 자동 스키마 생성을 Flyway 버전 관리 마이그레이션으로 전환한다.
- 초기 스키마와 이후 변경은 재현 가능한 SQL migration으로 관리하고, H2와 PostgreSQL 양쪽의 통합 테스트를 단계적으로 추가한다.

## 2. Asset-Listing 모델

- `AssetProfile(market + ticker)` 전략 카탈로그와 거래 가능한 상장 정보(`AssetListing`)를 분리했다.
- `AssetListing`은 자산 식별자, 시장, 티커, 표시명, 상장 상태 같은 사실만 소유한다. 전략 트랙·정책은 `AssetProfile`에 남긴다.
- 다음 단계에서는 보유 내역과 시세 조회가 상장 정보를 직접 참조하도록 마이그레이션하되, 기존 API의 `market`/`ticker` 계약은 호환 기간 동안 유지한다.

## 3. 운영 인증 완성

- `Member`와 로그인 제공자 식별자를 분리한 `AuthIdentity`, Google OIDC 로그인, URL의 `memberId` 소유권 검증은 구현됐다.
- 운영 환경에서 Google OAuth 동의 화면, 승인 리디렉션 URI, 세션 쿠키, 프론트엔드와 백엔드의 동일 출처 또는 프록시 경로를 배포 구성으로 검증한다.
- 역할 기반 관리자 권한과 URL에서 `memberId`를 제거하는 API 전환은 별도 설계 후 진행한다.

## 4. Toss 읽기 전용 연동

- 토스증권 연동은 계좌·보유 종목·거래 내역의 읽기 전용 동기화부터 검토한다.
- 토큰은 비밀 저장소/환경 설정으로만 다루고, 동의·재연결·동기화 시각·오류 상태를 사용자에게 명확히 표시한다.
- 주문 전송, 예약 주문 생성, 자동 매매는 범위에서 제외한다. 읽기 데이터는 기존 매매 기록과 출처·중복 처리 정책을 설계한 뒤 반영한다.

## 5. 테스트 단계

1. 현재 계산 서비스와 API 계약 테스트를 유지하고, 프론트엔드는 lint/build 및 주요 화면의 수동 흐름을 확인한다.
2. PostgreSQL/Flyway 전환 시 migration 검증과 repository 통합 테스트를 CI에 추가한다.
3. AuthIdentity 도입 시 인증 경계와 소유권(다른 member 접근 차단) API 테스트를 추가한다.
4. Toss 읽기 전용 연동 시 provider contract test, 토큰 미노출 검증, 부분 동기화·재시도 시나리오 테스트를 추가한다.
