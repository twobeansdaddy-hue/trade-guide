# Agent Task Contract

## Identity

- Task ID: `claude-toss-order-submission-provider-20260918`
- Owner: `Claude`
- Work mode: `scoped-implementation`
- Branch / worktree: `feature/toss-order-submission-provider`

## Outcome

`BrokerOrderSubmissionProvider`의 실제 Toss 구현체(`TossOrderSubmissionProvider`)를
공식 OpenAPI 스펙(`https://openapi.tossinvest.com/openapi-docs/latest/openapi.json`,
2026-09-18 직접 조회로 `POST /api/v1/orders` 요청/응답 스키마 확정)에 맞춰
구현한다. `BrokerProvider.TOSS_SECURITIES`에 `ORDER_SUBMISSION` capability를
선언해 실제로 열리게 한다.

**이 슬라이스가 끝나도 실거래는 사용자가 명시적으로 두 가지를 모두 할 때만**
**발생한다**: (1) `tradeguide.broker.order-execution.live-enabled=true` (2)
자기 자신의 Toss 계정을 앱 화면에서 연결하고 오토매매 opt-in 동의(`grant`)를
직접 생성. 둘 다 기본값이 꺼져 있고, 이 세션은 둘 다 켜지 않는다.

## Allowed Files

- `src/main/java/com/tradeguide/service/broker/TossOrderSubmissionProvider.java` (신규)
- `src/main/java/com/tradeguide/domain/broker/BrokerProvider.java` (수정 - TOSS_SECURITIES에 ORDER_SUBMISSION 추가)
- `src/test/java/com/tradeguide/service/broker/TossOrderSubmissionProviderTest.java` (신규)
- `src/test/java/com/tradeguide/service/broker/BrokerProviderRegistryTest.java` (수정 - ORDER_SUBMISSION 관련 기존 "미등록" 가정 테스트 갱신)
- `docs/LEARNING_LOG.md` (완료 상태 갱신만)

## Non-Goals And Guardrails

- **실제 Toss 계정으로 통합 테스트를 하지 않는다.** 이 세션은 사용자의 실제
  자격증명을 절대 입력받지 않는다(채팅에 붙여넣지 않음, 코드·테스트 픽스처에도
  넣지 않음) - 단위 테스트는 가짜 HTTP 서버(예: `MockRestServiceServer` 또는
  기존 `TossSecuritiesOrderHistoryProviderTest`가 쓰는 방식) 또는 인터페이스
  스텁으로만 검증한다. 실제 계정 연결·검증은 사용자가 앱 화면에서 직접 한다.
- **시장가(MARKET) 주문만 지원한다** - `BrokerOrderSubmissionRequest`가 이미
  이 범위로 설계됐다(지정가는 범위 밖). `price` 필드는 절대 채우지 않는다.
- **`confirmHighValueOrder`는 항상 기본값(false)으로 둔다** - 1억원 이상 주문을
  자동으로 확인 처리하지 않는다. Toss가 `confirm-high-value-required`로
  거부하면 그 코드를 그대로 `FAILED` 사유로 남긴다(사람이 검토해야 하는
  신호로 취급 - 자동으로 우회하지 않는다).
- 응답 파싱 실패·4xx·5xx·네트워크 오류는 전부 `BrokerOrderSubmissionResult
  .failed(code)`로 수렴한다 - 예외를 던져 `BrokerOrderExecutionRun`의
  `PENDING` 상태로 고아처럼 남기지 않는다(`BrokerOrderExecutionService`가
  이미 이 결과를 받아 `FAILED`로 기록한다).
- 실패 사유로는 Toss `ErrorResponse.error.code`(짧은 flat 식별자)만 저장한다.
  `message`(사람이 읽는 문장, 민감정보 가능성)나 `data`는 저장하지 않는다.
- `TossSecuritiesAccessTokenIssuer`(기존 인증 흐름)를 그대로 재사용한다 - 새
  인증 로직을 만들지 않는다. 401은 기존 `TossSecuritiesOrderHistoryProvider`와
  동일하게 토큰 캐시를 무효화한다.
- `clientOrderId`는 어댑터가 호출마다 새 UUID를 생성해 채운다 - Toss의
  멱등성 키로 쓰이지만, 이 슬라이스에는 재시도 로직이 없어 완전한 재시도
  안전성을 보장하지는 않는다(향후 재시도를 추가한다면 호출자가 안정적인 키를
  전달하도록 다시 설계해야 함 - Handoff에 명시).

## Acceptance Checks

- [x] `POST /api/v1/orders` 요청 본문이 스펙과 일치(symbol/side/orderType=MARKET/
      quantity/clientOrderId, price 없음, confirmHighValueOrder 기본값)하는지
      테스트
- [x] 200 응답의 `result.orderId`를 `BrokerOrderSubmissionResult.submitted`로
      매핑하는지 테스트
- [x] 4xx 에러 응답의 `error.code`를 `FAILED` 사유로 매핑하는지 테스트(예:
      `invalid-request`)
- [x] 401 응답 시 토큰 캐시를 무효화하는지 테스트
- [x] `BrokerProvider.TOSS_SECURITIES.getSupportedCapabilities()`에
      `ORDER_SUBMISSION`이 포함되고, 어댑터가 스프링 컨텍스트에 등록되면
      `BrokerProviderRegistry.isOrderSubmittable(TOSS_SECURITIES)`가 `true`인지
      테스트(기존 "미등록" 가정 테스트는 갱신)
- [x] `./gradlew test` 전체 스위트 통과(1088개)
- [x] `docs/LEARNING_LOG.md`에 완료 상태 한 줄 갱신, 그리고 "실거래를 켜려면
      무엇을 해야 하는지" 명시

## Handoff

- Files changed:
  - 신규: `TossOrderSubmissionProvider`, `TossOrderSubmissionProviderTest`
  - 수정: `BrokerProvider`(TOSS_SECURITIES에 ORDER_SUBMISSION 선언 추가),
    `BrokerProviderRegistryTest`(기존 "이중 차단" 테스트를 "어댑터 없으면
    여전히 닫힘" + "어댑터 등록하면 열림" 두 테스트로 분리, `registryWith`
    4-인자 오버로드 추가), `BrokerProviderControllerTest`(계약 범위 밖이었으나
    캡슐 목록이 하드코딩돼 있어 회귀로 깨짐 - `ORDER_SUBMISSION` 추가해 수정)
- Verification run: `./gradlew test` 전체 스위트 1088개 통과, 회귀 없음
- API / data-model impact: `GET /api/broker-providers` 등 provider 카탈로그
  응답의 `supportedCapabilities`에 TOSS_SECURITIES 항목이 `ORDER_SUBMISSION`을
  추가로 포함하게 됨(프론트엔드가 이 필드를 소비한다면 영향 있음 - 이번
  슬라이스는 프론트엔드를 확인하지 않았다).
- 사용자가 다음에 직접 해야 하는 것: 앱 화면에서 Toss 계정 연결(자격증명은
  앱에만 입력, 이 세션에는 공유되지 않았음) → 오토매매 opt-in 동의 생성 →
  운영 판단에 따라 `TRADEGUIDE_BROKER_ORDER_EXECUTION_LIVE_ENABLED=true` 설정.
  이 세 가지 중 하나라도 안 하면 실거래는 발생하지 않는다.
