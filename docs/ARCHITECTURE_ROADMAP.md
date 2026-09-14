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

- 토스증권 연동은 계좌·보유 종목·주문 이력의 읽기 전용 조회를 구현했다. 주문 이력은 미리보기, 정합성 대조, 사용자 승인 후 `BROKER_ORDER_HISTORY` 원장 반영까지 지원하며, 시장 데이터 제공자 선택과 증권 계좌 연결은 별도 모델로 관리한다. 현재 주문 이력의 원장 반영 시장은 미국으로 제한한다.
- 사용자별 `BrokerConnection`은 암호화된 자격 증명, 마스킹된 계좌 표시, 연결 상태·검증 시각·동기화 상태만 노출한다. 원문 키·토큰·계좌 참조값은 API 응답과 로그에 포함하지 않는다.
- 단일 애플리케이션 설정 키로 여러 사용자의 토스 계좌를 처리하지 않는다. 개인 베타와 공개 다중 사용자 서비스의 보안·키 관리 경계를 먼저 확정한다.
- 계좌 연결 확인, 계좌 목록, 실시간 보유 종목 미리보기와 사용자가 명시적으로 갱신하는 보유 종목 스냅샷을 구현했다. 저장된 최신 스냅샷은 외부 증권사 재호출 없이 Trade Guide 보유 종목과 비교한다.
- 비교 결과가 `ONLY_IN_BROKER`인 항목만 사용자가 한 건씩 명시적으로 승인하면 `BROKER_OPENING_BALANCE` 출처의 매수 원장 기록으로 반영할 수 있다. 승인 이력은 별도 감사 테이블에 남기며, 승인 취소는 해당 개시 잔고 원장 기록을 되돌리고 이력 상태를 `REVOKED`로 남긴다. 수량 불일치와 Trade Guide에만 존재하는 종목은 자동 수정하지 않는다.
- 주문 전송, 예약 주문 생성, 자동 매매는 범위에서 제외한다. 상세 기준은 `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`를 따른다.
- 포트폴리오별 시장 데이터 제공자 설정 API와 설정 UI는 구현됐다. 현재 `TWELVE_DATA`만 선택 가능하며, 토스증권과 Yahoo Finance는 연결·운영 조건이 충족되기 전까지 목록에서 준비 중 상태로만 표시한다.
- 사용자별 토스증권 연결 저장과 설정 화면, 토큰 발급을 통한 연결 확인, 계좌 목록 조회, 보유 종목 조회가 구현됐다. 자격 증명은 제공자 명세가 선언한 항목마다 한 행씩 `broker_connection_secret_values`에 AES-256-GCM으로 암호화해 저장하며(항목 단위 키 버전, `(연결, 항목 키)` 유니크), API 응답에는 자격 증명을 반환하지 않는다. 계좌 참조값은 `broker_accounts`에 따로 암호화해 둔다. 암호화 키가 없는 환경에서는 연결 요청을 `503`으로 차단한다.
- 자격 증명 모델은 제공자 중립이다. 요청 본문은 `credentials` 맵과 기존 `clientId`/`clientSecret` 최상위 항목을 모두 받으며(두 형태 동시 전송은 `400`), 어댑터 계약은 `BrokerCredentials` 값 객체를 받는다. 복호화는 `BrokerCredentialLoader` 한 곳에서만 수행한다. 두 번째 증권사를 추가할 때 DTO·스키마·어댑터 인터페이스를 고치지 않는다.
- 기능 관문은 `BrokerProviderRegistry` 한 곳에 모았다. 연결 검증·보유 종목·주문 이력 모두 "제공자의 기능 선언 + 어댑터 등록"을 함께 확인하며, 열리지 않은 기능은 `503` `BROKER_CAPABILITY_UNAVAILABLE`로 거부한다. `GET /api/broker-providers`는 선언(`supportedCapabilities`)과 실제 가용 기능(`availableCapabilities`), 조회 가능 시장(`supportedMarkets`)과 원장 반영 가능 시장(`ledgerWritableMarkets`)을 구분해 노출한다. 화면은 `availableCapabilities`로 버튼 활성화를 판단한다.
- 보유 종목 스냅샷은 `broker_holding_snapshots`와 `broker_holding_snapshot_items`에 별도로 저장한다. 조회·비교는 저장본을 사용하고, 증권사 API 호출은 사용자가 `보유 종목 갱신`을 실행할 때만 발생한다.

## 5. 테스트 단계

1. 현재 계산 서비스와 API 계약 테스트를 유지하고, 프론트엔드는 lint/build 및 주요 화면의 수동 흐름을 확인한다.
2. PostgreSQL/Flyway 전환 시 migration 검증과 repository 통합 테스트를 CI에 추가한다.
3. AuthIdentity 도입 시 인증 경계와 소유권(다른 member 접근 차단) API 테스트를 추가한다.
4. Toss 읽기 전용 연동의 provider 계약, 토큰 미노출, 계좌 소유권, 스냅샷 저장·비교, 명시적 개시 잔고 반영·취소, 주문 이력 미리보기·정합성 대조·승인 반영 테스트는 구현했다. 실제 장기 조회 범위와 WTS 약관 확인, 부분 동기화 재시도 정책, 국내 시장 주문 이력의 원장 반영은 후속 범위다.
