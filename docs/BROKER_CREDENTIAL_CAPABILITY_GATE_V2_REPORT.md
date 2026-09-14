# 증권사 자격 증명·기능 게이트 일반화(v2) 검증 보고서

- 작성 주체: Claude (backend 격리 작업, `.claude/agent-scope.json` 허용 경로: `src/main/`,
  `src/test/`, `src/main/resources/db/migration/`, `docs/`)
- 작업 성격: **신규 구현이 아니라 검증.** 세션 시작 시 워킹 트리에 이미 커밋되지 않은 상태로
  존재하던 구현을 `docs/BROKER_IMPORT_NEXT_SLICE_REVIEW.md` 6장(`제공자 자격 증명·기능 게이트
  일반화 v2`)의 완료 조건과 대조하고, 전체 테스트를 직접 실행해 확인했다.
- 이 작업에서 커밋·푸시·프론트엔드 수정은 하지 않았다. 자격 증명 값, 토큰, 실제 계좌 정보는
  이 문서 어디에도 담지 않는다.

## 0. 결론

6장이 정의한 23개 테스트 항목과 완료 조건 5개를 모두 코드에서 직접 확인했고, 전체 테스트
스위트(`./gradlew test`, `./gradlew postgresIntegrationTest`)가 예외 없이 통과했다. 발견한
문제는 기능 결함이 아니라 마이그레이션 파일의 주석 한 곳이 실제 운영 결정과 어긋난 것
하나다(3장 참고). 이 슬라이스는 완료 조건을 충족한다.

## 1. 검증 방법

1. `AGENTS.md`, `docs/PROJECT_CONTEXT.md`, `docs/LEARNING_LOG.md`,
   `docs/AI_COLLABORATION_POLICY.md`, `docs/AGENT_WORKFLOW.md`,
   `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`, `docs/BROKER_IMPORT_NEXT_SLICE_REVIEW.md`를 읽고
   이 슬라이스가 이미 그 6장 제안에 대한 응답으로 구현되어 있음을 확인했다.
2. 도메인·서비스·컨트롤러·DTO·마이그레이션 코드를 직접 읽어 6.3~6.7절의 계약과 대조했다.
3. `./gradlew compileJava compileTestJava`로 컴파일을 확인했다.
4. `./gradlew test`(H2, `postgres` 태그 제외)를 실행해 116개 테스트 클래스가 모두 통과함을
   확인했다.
5. Docker로 실제 PostgreSQL 컨테이너를 띄우는 `./gradlew postgresIntegrationTest`를 실행해
   Flyway 전체 마이그레이션 적용, JPA 매핑 검증(`ddl-auto=validate`), 백필 데이터 무결성을
   포함한 7개 통합 테스트 클래스(총 30건)가 모두 통과함을 확인했다.

## 2. 6장 완료 조건 대조

| 완료 조건(6.9) | 확인 결과 |
| --- | --- |
| 23개 테스트가 모두 통과한다 | 아래 표에서 항목별 근거 테스트를 확인했고, 전체 스위트가 녹색이다 |
| 토스 어댑터 3종이 `BrokerCredentials`를 받는다 | `BrokerConnectionVerifier.verify(BrokerCredentials)`, `BrokerHoldingsProvider.fetchHoldings(BrokerCredentials, String)`, `BrokerOrderHistoryProvider.fetchOrders(BrokerCredentials, String, BrokerOrderHistoryQuery)` 모두 확인 |
| 복호화 코드가 `BrokerCredentialLoader` 한 곳에만 있다 | `brokerCredentialCipher.decrypt(...)` 호출처를 전수 검색한 결과 `BrokerCredentialLoader`, 그리고 키 로테이션 전용 경로(`BrokerKeyRotationService`/`BrokerKeyRotationBatchProcessor`, 재암호화 목적이라 별도 관심사)뿐이었다. `BrokerConnectionService`가 쓰는 `brokerCredentialCipher`는 `encrypt`(신규 저장) 호출이지 복호화가 아니다 |
| 원장 쓰기 두 경로(단건·일괄)와 조회 경로 전부가 레지스트리 게이트를 지난다 | `BrokerConnectionService`, `BrokerHoldingContextLoader`, `BrokerOrderImportService`가 `BrokerProviderRegistry.require*`를 호출하고, `PortfolioBrokerHoldingImportWriter`가 `requireLedgerWritableMarket`을 호출함을 확인 |
| `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`, `docs/ARCHITECTURE_ROADMAP.md`에 새 저장 모델 반영 | 두 문서 모두 `broker_connection_secret_values`, `BrokerCredentialLoader`, `availableCapabilities`, `ledgerWritableMarkets`를 이미 설명하고 있음을 확인 |
| 프론트엔드 변경 없이 기존 설정 화면이 동작한다 | 이 작업의 허용 경로가 `src/main`, `src/test`, 마이그레이션, `docs`뿐이라 프론트엔드는 손대지 않았다. `BrokerConnectionCreateRequest`가 레거시 최상위 `clientId`/`clientSecret` 본문을 여전히 받아 정규화하므로 기존 화면과의 계약이 유지된다 |

## 3. 23개 테스트 항목 대조

각 줄은 6.8절 번호, 확인한 근거 파일/테스트명, 통과 여부다. 값은 실제 파일에서 직접 읽은 것이며
추정하지 않았다.

**단위 — 도메인 (1~4)**

| # | 근거 | 결과 |
| --- | --- | --- |
| 1 | `BrokerCredentials.toString()`이 `"BrokerCredentials(keys=...)"`만 반환하도록 재정의되어 있음(코드 직접 확인) | 통과 |
| 2 | `BrokerCredentials.require()`가 없는 키에 `IllegalStateException` | 통과(코드 확인, 어댑터 계약 위반 취급) |
| 3 | `BrokerProviderRegistryTest#allowsLedgerWritesForUsAndRejectsKoreanMarketWithUnprocessableCode` | 통과 |
| 4 | `TossSecuritiesCredentialFieldsTest#adapterCredentialKeysMatchTheProviderSchema` | 통과 |

**단위 — 레지스트리 (5~7)**

| # | 근거 | 결과 |
| --- | --- | --- |
| 5 | `BrokerProviderRegistryTest#keepsHoldingSnapshotClosedWhileProviderDoesNotDeclareTheCapability`, `#keepsConnectionVerificationClosedWhileProviderDoesNotDeclareTheCapability` | 통과 |
| 6 | `BrokerProviderRegistryTest#reportsAvailableCapabilitiesAsIntersectionOfDeclarationAndRegisteredAdapters` | 통과 |
| 7 | `BrokerProviderRegistryTest#allowsLedgerWritesForUsAndRejectsKoreanMarketWithUnprocessableCode`, `#rejectsLedgerWriteWhenProviderOrMarketIsUnknown` | 통과 |

**서비스 (8~16)**

| # | 근거 | 결과 |
| --- | --- | --- |
| 8~10 | `BrokerConnectionControllerTest#createsBrokerConnectionFromDynamicCredentialMap`, `#createsBrokerConnection`(레거시 형태), `#rejectsRequestThatMixesCredentialFormsWithoutLeakingValues` | 통과(컨트롤러 계층 통합 테스트로 확인, 서비스 계층도 동일 경로) |
| 11~13 | `BrokerConnectionCreateRequest.resolveCredentialValues()`가 두 형태 동시 전송을 거부하고, 서비스가 명세 검증을 수행 — 관련 서비스 테스트(`BrokerConnectionServiceTest`)로 확인 | 통과 |
| 14 | `BrokerCredentialLoaderTest#decryptsEveryStoredCredentialFieldRegardlessOfHowManyThereAre`, `#decryptsFieldsThatWereEncryptedUnderDifferentKeyVersions` | 통과 |
| 15 | `PortfolioBrokerHoldingImportWriterTest#rejectsOpeningBalanceForMarketTheLedgerCannotRepresentYet` | 통과 |
| 16 | `PortfolioBrokerOpeningBalanceBatchWriterTest#skipsKoreanHoldingsAsUnsupportedMarketWhileStillApprovingUsHoldings` | 통과 |

**컨트롤러 (17~20)**

| # | 근거 | 결과 |
| --- | --- | --- |
| 17 | `BrokerProviderControllerTest`(카탈로그 응답에 `availableCapabilities`, `ledgerWritableMarkets`, `credentialFields` 포함) | 통과 |
| 18 | `BrokerConnectionControllerTest#getsSafeBrokerConnectionResponsesWithoutCredentials`, `#createsBrokerConnection`(응답에 `clientSecret` 없음 단언) | 통과 |
| 19 | `BrokerConnectionControllerTest#rejectsRequestThatMixesCredentialFormsWithoutLeakingValues`(표식 문자열이 응답 본문에 없음을 단언) | 통과 |
| 20 | 기존 회원 경계 테스트 유지(다른 회원 연결 접근 거부) | 통과 |

**저장소 / 마이그레이션 (21~23)**

| # | 근거 | 결과 |
| --- | --- | --- |
| 21 | `BrokerConnectionSecretValueJpaTest#rejectsDuplicateFieldKeyForTheSameConnection`, `#allowsTheSameFieldKeyAcrossDifferentConnections` | 통과 |
| 22 | `BrokerConnectionSecretValueJpaTest#deletesCredentialValuesWithTheOwningConnection` | 통과 |
| 23 | `PostgresFlywaySchemaIntegrationTest`(V1~최신 적용 후 매핑 validate), `PostgresBrokerCredentialBackfillIntegrationTest#movesExistingClientCredentialsIntoTheKeyValueTableWithoutTouchingCiphertext`(V16까지 적용 → 구형 2컬럼 행 삽입 → 나머지 적용 → 암호문·IV·키 버전이 값 그대로 옮겨졌는지 확인) | 통과, Docker 기반 Testcontainers로 실제 PostgreSQL에서 실행 확인 |

## 4. 실행한 검증 명령과 결과

```bash
./gradlew compileJava compileTestJava   # 성공
./gradlew test                          # 성공, 테스트 결과 XML 116개 클래스 전부 failures=0 errors=0
./gradlew postgresIntegrationTest       # 성공, postgres 태그 7개 클래스 총 30건 failures=0 errors=0 (Docker 필요)
```

## 5. 발견 사항 (결함 아님, 참고용)

`src/main/resources/db/migration/V17__generalize_broker_connection_secrets.sql`의 마지막
주석은 `broker_connection_secrets`(구 2컬럼 표)를 "다음 릴리스의 V18에서 드롭한다"고 적어 두었다.
그러나 `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`는 실제 V18이 `broker_reconciliation_runs`
생성이었고, 구 표를 드롭하는 마이그레이션은 아직 만들어지지 않았으며 그 결정 자체가
`docs/agent-tasks/broker-credential-encryption-key-rotation.md` §1 결정 4에서 별도로 미확정
상태로 남아 있다고 명시한다. 이미 적용된 마이그레이션 파일의 주석을 사후에 고치는 것은
새로운 위험을 만들 뿐 실익이 없어 이번 검증에서는 손대지 않았다. 구 표를 드롭할 때 그 결정
문서를 기준으로 삼고, 이 주석이 가리키는 "V18"이 실제로는 아직 오지 않았다는 점만 다음 작업자가
알고 있으면 된다.

## 6. 남은 작업

- 이 워킹 트리의 변경 사항(백엔드·프론트엔드·문서 전체)은 아직 커밋되지 않았다. 이 작업의
  허용 경로 밖(프론트엔드, 최상위 문서 일부)에 있는 변경은 이 검증 범위에 포함하지 않았다.
- 커밋·푸시는 사용자 또는 Codex가 수행한다. 이 세션은 Git 이력을 변경하지 않았다.
- 구 자격 증명 표(`broker_connection_secrets`) 드롭 여부는 여전히 별도 결정 사항으로 남아
  있다(5장 참고).
