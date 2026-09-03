# Architecture Roadmap

웹 MVP 이후의 구조 전환 계획이다. 아래 항목은 구현 확정이 아니라, 인증·데이터 모델·외부 연동처럼 구현 전에 별도 승인과 설계 검토가 필요한 순서다.

## 1. PostgreSQL과 Flyway

- 로컬 H2 학습 환경과 운영 PostgreSQL 프로필을 분리한다.
- 현재 JPA 자동 스키마 생성을 Flyway 버전 관리 마이그레이션으로 전환한다.
- 초기 스키마와 이후 변경은 재현 가능한 SQL migration으로 관리하고, H2와 PostgreSQL 양쪽의 통합 테스트를 단계적으로 추가한다.

## 2. Asset-Listing 모델

- `AssetProfile(market + ticker)` 전략 카탈로그와 거래 가능한 상장 정보(`AssetListing`)를 분리했다.
- `AssetListing`은 자산 식별자, 시장, 티커, 표시명, 상장 상태 같은 사실만 소유한다. 전략 트랙·정책은 `AssetProfile`에 남긴다.
- 미국 종목 검색은 현재 `AssetListing`과 Twelve Data의 조회 결과를 합쳐 제공한다. 외부 검색 결과를 즉시 영속화하지 않아 카탈로그 수집과 사용자 검색을 분리한다.
- 보유 종목 평가(`PortfolioValuationService`)는 현재가를 조회하기 전에 `AssetListing`이 존재하고 `ACTIVE` 상태인지 검증한다. 상장 정보가 없거나 비활성 상태면 외부 시세 API를 호출하지 않고 즉시 오류로 응답한다. 기존 `market`/`ticker` 응답 계약은 그대로 유지했다.
- V4 Flyway migration은 이미 매매 기록이 존재하는 `market`/`ticker` 조합 중 `asset_listings`에 없는 것을 `ACTIVE` 상태로 채워, 기존 PostgreSQL 데이터의 보유 평가가 이번 검증으로 갑자기 막히지 않도록 했다.
- 매매 기록 생성(`TradeTransactionService`)은 이제 `AssetListingService.ensureActiveListingForTrade`로 거래하려는 `market`/`ticker`의 `ACTIVE` 상장 정보를 보장한 뒤에만 저장한다. 이미 `ACTIVE`인 상장은 재사용하고, 없으면 `AssetSearchProvider` 검색 결과 중 `market`/`ticker`가 정확히 일치하는 항목으로만 새 `ACTIVE` 상장을 생성한다. 일치하는 항목이 없거나 기존 상장이 `INACTIVE`이면 거래를 저장하지 않고 입력 오류로 응답하며, 비활성 상장을 자동으로 재활성화하지 않는다. 단순 검색 조회 경로(`searchActiveListings`)는 계속 외부 결과를 영속화하지 않는다.

## 3. 운영 인증 완성

- `Member`와 로그인 제공자 식별자를 분리한 `AuthIdentity`, Google OIDC 로그인, URL의 `memberId` 소유권 검증(`MemberAccessService`)은 구현됐다.
- React 로그인 UI(`SignInPage`)와 `/api/auth/me` 연동(`authApi.getCurrentMember`, `PortfolioProvider`)도 이미 구현됐다. `VITE_LOCAL_MEMBER_ID`가 없으면 `/api/auth/me`를 조회하고, 401이면 `SignInPage`의 `/oauth2/authorization/google` 버튼으로 로그인 화면을 보여준다.
- 운영 환경에서 Google OAuth 동의 화면, 승인 리디렉션 URI, 세션 쿠키, 프론트엔드와 백엔드의 동일 출처 또는 프록시 경로를 배포 구성으로 검증한다.
- 역할 기반 관리자 권한과 URL에서 `memberId`를 제거하는 API 전환은 별도 설계 후 진행한다.

### 운영 배포 시 사용자가 별도로 해야 하는 Google Cloud Console 설정 (체크리스트)

아래 항목은 Claude가 대신 수행할 수 없다. 실제 클라이언트 ID/보안 비밀은 코드, 문서, 로그, 대화에 기록하지 않는다.

- [ ] Google Cloud Console에서 OAuth 동의 화면을 구성하고 `openid`, `profile`, `email` 범위를 확인한다.
- [ ] "웹 애플리케이션" 유형의 OAuth 2.0 클라이언트 ID를 생성한다.
- [ ] "승인된 리디렉션 URI"에 운영 도메인 기준 `{배포 도메인}/login/oauth2/code/google`을 등록한다(Spring Security 기본 콜백 경로). 로컬 개발용 `http://localhost:8080/login/oauth2/code/google`은 필요하면 별도로 등록한다.
- [ ] "승인된 자바스크립트 원본"에 프론트엔드가 실제로 서빙되는 origin을 등록한다. 백엔드와 다른 origin으로 배포하면 세션 쿠키 공유가 깨질 수 있으므로, 가능하면 동일 출처 또는 리버스 프록시 경로로 배치한다.
- [ ] 발급된 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`은 운영 서버의 환경 변수로만 주입한다. `tradeguide.auth.enabled=true`도 함께 설정한다(`SecurityConfig`는 두 값이 비어 있으면 기동 시 `IllegalStateException`을 던진다).
- [ ] HTTPS 배포에서 세션 쿠키에 `Secure` 속성이 적용되는지, 리버스 프록시가 TLS를 종료하는 경우 `X-Forwarded-*` 헤더 처리 설정이 되어 있는지 확인한다.
- [ ] 배포 후 `/oauth2/authorization/google` → Google 동의 화면 → `/login/oauth2/code/google` 콜백 → `GET /api/auth/me` 흐름을 실제로 로그인해 수동 검증한다.

## 4. Toss 읽기 전용 연동

- 토스증권 연동은 계좌·보유 종목·거래 내역의 읽기 전용 동기화부터 검토한다.
- 토큰은 비밀 저장소/환경 설정으로만 다루고, 동의·재연결·동기화 시각·오류 상태를 사용자에게 명확히 표시한다.
- 주문 전송, 예약 주문 생성, 자동 매매는 범위에서 제외한다. 읽기 데이터는 기존 매매 기록과 출처·중복 처리 정책을 설계한 뒤 반영한다.

## 5. 테스트 단계

1. 현재 계산 서비스와 API 계약 테스트를 유지하고, 프론트엔드는 lint/build 및 주요 화면의 수동 흐름을 확인한다.
2. PostgreSQL/Flyway 전환 시 migration 검증과 repository 통합 테스트를 CI에 추가한다.
3. AuthIdentity 도입 시 인증 경계와 소유권(다른 member 접근 차단) API 테스트를 추가한다.
4. Toss 읽기 전용 연동 시 provider contract test, 토큰 미노출 검증, 부분 동기화·재시도 시나리오 테스트를 추가한다.
