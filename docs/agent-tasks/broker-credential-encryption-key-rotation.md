# S7 백엔드 설계 제안 — 증권사 자격 증명 암호화 키 로테이션

## Identity

- Task ID: `TASK-S7-BROKER-KEY-ROTATION`
- Owner: `Claude`(이 설계 제안, 문서 전용) / `Codex`(구현 착수 여부·승인 판단)
- Work mode: `research`(아키텍처 리뷰) — 이 문서 자체는 코드·설정·스키마를 변경하지 않는다.
- 범위: `docs/agent-tasks/broker-credential-encryption-key-rotation.md` 신규 작성만. `src/**`,
  `frontend/**`, 다른 `docs/**`, 설정 파일은 이 작업에서 읽기 전용이었고 변경하지 않았다.

## 0. 요약

`BrokerConnectionSecretValue.encryption_key_version`(값 단위, V17)과
`BrokerAccount.encryption_key_version`(계좌 단위, V8)은 이미 존재하며 무중단 키 로테이션을
염두에 두고 설계된 컬럼이다(§2.1). 하지만 실제 로테이션을 수행할 수 있는 코드는 아직
하나도 없다: 현재 `AesGcmBrokerCredentialCipher`는 키를 하나만 들고 있고, 저장된 버전
값을 읽지 않은 채 항상 그 하나의 키로만 복호화한다(§2.2). 이 문서는

1. 저장소에서 실제로 확인한 사실과, 로테이션을 만들려면 반드시 먼저 고쳐야 하는 코드
   전제조건을 §2에 분리해 적는다.
2. 값 없이 키 식별자·버전만 다루는 설정 계약 초안을 §4에 제안한다.
3. 사전 점검·트랜잭션/재시도/멱등성/동시성·롤백·감사·관리자 경계·마이그레이션·PostgreSQL
   테스트 전략을 §5~§11에 설계한다.
4. 임의로 고를 수 없는 값(트리거 방식, 설정 형태, 배치 크기, 레거시 테이블 정리 시점,
   키 보존 기간)은 §1에 결정표로 모아 승인을 요청한다.

이 문서는 정책·구현이 아니라 제안이다. `docs/AI_COLLABORATION_POLICY.md`에 따라
인증·인가·브로커 API·DB 스키마 변경은 사용자 승인 후에만 구현한다.

## 1. 결정이 필요한 사항 (사용자/Codex 승인 필요)

| # | 결정 | 권장안 | 근거 |
| --- | --- | --- | --- |
| 1 | 로테이션 실행 트리거 | **운영자가 직접 실행하는 오프라인 진입점**(전용 Gradle 태스크 또는 `CommandLineRunner` + 명시적 옵트인 프로퍼티). 새 인증 HTTP 엔드포인트는 이번 슬라이스에 두지 않는다. | `docs/PROJECT_CONTEXT.md:110`은 인증 활성화 상태에서 `/api/admin/**`을 차단하며 역할 기반 관리자 모델이 아직 없다고 명시한다. 역할 모델 없이 `/api/admin/broker-connections/rotate-key` 같은 엔드포인트를 만들면 그 자체가 무권한 경계가 된다. §9 참고. |
| 2 | 다중 키 설정 형태 | `tradeguide.broker.encryption-keys[].version`+`value`(버전별 Base64 32바이트 키) 목록과 `tradeguide.broker.encryption-current-version`(신규 암호화에 쓸 버전)을 추가하고, 기존 단일 `tradeguide.broker.encryption-key`(`BROKER_CREDENTIAL_ENCRYPTION_KEY`)는 목록이 비어 있을 때 버전 1로 취급해 하위 호환한다. | 지금은 프로퍼티 하나에 키 하나뿐이라 로테이션 자체가 불가능하다(§2.2). 기존 배포가 새 목록 없이도 계속 기동해야 한다. 정확한 프로퍼티 이름·환경변수 인덱싱 방식(`_V1`, `_V2` 등)은 값이 아니라 이름 규칙 결정이므로 여기서 승인받는다. |
| 3 | 배치 크기·페이지당 처리 건수 | 기본값 **200행/트랜잭션** 권장(§6). | 이 프로젝트는 손절률 같은 투자 상수뿐 아니라 운영 안전값도 근거 없이 고르지 않는 관행을 이미 갖고 있다(`docs/agent-tasks/broker-order-import-range-limit-and-call-guard.md` §1의 366일·5초 쿨다운 결정 참고). 이 값은 그 관행을 따라 별도 승인이 필요한 임의 상수다. |
| 4 | 레거시 `broker_connection_secrets` 테이블 정리 시점 | 로테이션 슬라이스와 **분리된 별도 마이그레이션**으로, 가능하면 로테이션 이전에 드롭. | §2.3에서 확인했듯 `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md:127-132`는 "V18에서 드롭"이라 적었지만 실제 V18은 `broker_reconciliation_runs` 생성이고 이 옛 표는 아직 지워지지 않았다. 로테이션 로직 자체는 새 표만 건드리므로 반드시 먼저 지울 필요는 없지만, 장애 대응 중 두 개의 "옛 암호문" 표가 동시에 남아 있으면 혼란의 소지가 있다. |
| 5 | 폐기 키 보존 기간 | 로테이션 실행이 "잔여 구버전 행 0건"을 보고한 뒤에도, **다음 한 번의 로테이션 주기(운영자가 정하는 기간) 동안** 이전 키를 설정에서 완전히 제거하지 않는다. | 이 값은 검증되지 않은 보안 정책 상수이며, 사고 대응 중 §7의 재로테이션(롤백)이 이전 키 없이는 불가능하다. 구체적 기간은 이 저장소 어디에도 근거가 없으므로 사용자가 정한다. |

3·5번이 늦어져도 1·2·4번은 먼저 구현에 착수할 수 있다. 다만 1번(트리거 방식)이 정해지지
않으면 §9의 경계를 구현할 수 없고, 2번(설정 계약)이 정해지지 않으면 §5의 사전 점검을
구현할 수 없다.

## 2. 저장소에서 확인한 현재 상태 (사실)

### 2.1 값 단위 키 버전은 이미 존재한다

- `broker_connection_secret_values.encryption_key_version`은 V17에서 값(행) 단위로
  도입됐다(`src/main/resources/db/migration/V17__generalize_broker_connection_secrets.sql:15`).
  같은 연결 안에서 항목마다 버전이 다를 수 있다는 설계 의도가 엔티티 주석에도 그대로
  남아 있다(`src/main/java/com/tradeguide/domain/broker/BrokerConnectionSecretValue.java:23-25`).
- `broker_accounts.encryption_key_version`은 V8에서 계좌 단위로 추가됐다
  (`src/main/resources/db/migration/V8__create_portfolio_broker_links.sql:1-2`, 기존 행은
  기본값 1로 채워졌다).
- 즉 **스키마는 이미 로테이션을 위한 준비가 되어 있다.** 이 문서가 새로 스키마를 바꿀
  필요가 있는 부분은 `broker_connection_secret_values`/`broker_accounts` 자체가 아니라
  로테이션 실행 기록용 새 표(§10)뿐이다.

### 2.2 그러나 암호기는 버전을 실제로 쓰지 않는다 — 로테이션의 전제조건

- `AesGcmBrokerCredentialCipher`는 생성자에서 `SecretKey` 필드 하나만 만든다
  (`src/main/java/com/tradeguide/service/broker/AesGcmBrokerCredentialCipher.java:25-48`).
- `encrypt()`는 항상 `KEY_VERSION = 1`을 상수로 반환한다(같은 파일 23행, 56-80행).
- `decrypt(EncryptedBrokerCredential encryptedCredential)`은 인자로 받은
  `encryptedCredential.keyVersion()`을 **전혀 읽지 않는다.** 항상 하나뿐인
  `secretKey`로만 복호화한다(같은 파일 83-98행).
- `BrokerCredentialLoader`는 저장된 `getEncryptionKeyVersion()` 값을
  `EncryptedBrokerCredential`에 그대로 담아 넘기지만
  (`src/main/java/com/tradeguide/service/broker/BrokerCredentialLoader.java:54-58`,
  76-80행), 그 값을 실제로 소비하는 곳이 없다.

**결론.** 지금 코드는 "버전을 저장은 하지만 두 번째 키를 가질 수 없는" 상태다. 키를
하나 더 추가하고 예전 버전 값이 달린 행을 복호화하려는 순간 현재 코드는 무조건 새
키로만 시도해 실패한다. 로테이션을 설계하려면 **암호기가 버전별 다중 키를 들고
`decrypt()`가 버전으로 키를 선택하도록 먼저 바뀌어야 한다.** 이는 로테이션의 부수
효과가 아니라 선행 조건이다(§12의 1번).

### 2.3 문서와 실제 마이그레이션이 어긋난 지점

`docs/BROKER_AND_PROVIDER_ARCHITECTURE.md:127-132`는 다음과 같이 적고 있다.

> `broker_connection_secrets`(이전의 고정 두 열 표)는 V17로 대체된다 ... 옛 표는 한
> 릴리스 동안 롤백 여유로 남겨 두고 V18에서 드롭한다.

그러나 실제 `V18__create_broker_reconciliation_runs.sql`은 사용자 주도 원장 정합성 점검
(S5) 스키마를 새로 만드는 마이그레이션이며 `broker_connection_secrets`를 드롭하지
않는다. 그 표는 저장소에 아직 존재한다(`grep`으로 `V6__create_broker_connections.sql:18`의
생성 구문 확인, 이후 어떤 마이그레이션에도 `DROP TABLE broker_connection_secrets`가
없음을 확인). 애플리케이션 코드는 V17 이후 이 표를 읽거나 쓰지 않으므로 기능상 문제는
없지만, 문서의 "V18에서 드롭" 서술은 사실이 아니다. §1 결정 4는 이 어긋남을 별도
마이그레이션으로 바로잡을지 묻는다.

### 2.4 접근 경로와 동시성 관련 기존 제약

- `BrokerConnectionSecretValue`는 생성자만 있고 ciphertext/iv/버전을 바꾸는 setter가
  없다(`BrokerConnectionSecretValue.java:62-82`). `BrokerConnection.replaceSecretValues(...)`는
  기존 `secretValues` 리스트를 **통째로 비우고 다시 채운다**
  (`OneToMany(..., cascade = CascadeType.ALL, orphanRemoval = true)` +
  `this.secretValues.clear(); ... this.secretValues.add(value)`,
  `BrokerConnection.java:63-69`, 101-119행). 즉 오늘 존재하는 유일한 쓰기 경로는
  "값이 바뀌었을 때 전체 자격 증명을 교체"하는 사용자 주도 경로이지, "같은 평문을
  다른 키로 다시 감싸는" 로테이션에 맞는 부분 갱신 경로가 아니다.
- `BrokerAccount.refreshFromProvider(...)`도 ciphertext/iv/버전과 함께
  `status = ACTIVE`, `detachedAt = null`을 같이 덮어쓴다(`BrokerAccount.java:85-99`).
  이 메서드를 그대로 로테이션에 재사용하면 `DETACHED` 계좌가 로테이션만으로
  다시 `ACTIVE`가 되는 부작용이 생긴다. 순수 재암호화 전용 메서드가 별도로 필요하다.
- `BrokerConnectionSecretValue`와 `BrokerAccount`를 직접 조회·갱신하는 레포지토리는
  존재하지 않는다(`find src/main/java -iname "*SecretValue*Repository*"`,
  `-iname "BrokerAccountRepository.java"` 모두 결과 없음). 지금은 오직
  `BrokerConnectionRepository` → `BrokerConnection` 애그리게이트를 통해서만 접근한다.
  전체 회원의 구버전 행을 페이지 단위로 훑는 로테이션 배치는 이 경로로는 만들 수 없다.
- 낙관적 잠금(`@Version`) 컬럼은 두 엔티티 어디에도 없다. 로테이션 배치의 쓰기와 사용자의
  일반 쓰기가 같은 행에서 경합해도 이를 감지할 매커니즘이 현재 없다.
- 애플리케이션 어디에도 `@Scheduled`가 없다(`grep -rln "@Scheduled" src/main` 결과 없음).
  자동 주기 로테이션 인프라는 전무하다.
- `BrokerDuplicateCallGuard`는 인메모리 `ConcurrentHashMap` 기반이며, 스스로 "다중 인스턴스
  배포에서는 인스턴스별로 따로 논다"고 문서화한 한계를 갖고 있다
  (`src/main/java/com/tradeguide/service/broker/BrokerDuplicateCallGuard.java:1-19`).
  로테이션 실행 중복 방지를 이 패턴으로 베끼면 같은 한계를 그대로 물려받는다. §6.3에서
  DB 레벨 잠금으로 대체하는 이유다.
- 애플리케이션 코드 어디에도 `org.slf4j`/로거 사용이 없다(`grep -rln "org.slf4j\|LoggerFactory" src/main/java` 결과 0건). 이 저장소의 유일한 확립된 감사 흔적 방식은
  `broker_reconciliation_runs`처럼 DB에 실행 기록 행을 남기는 것이다
  (`docs/BROKER_AND_PROVIDER_ARCHITECTURE.md:207-209`). §8은 이 관행을 그대로 따른다.

## 3. 목표와 비목표

**목표.** 값 단위 `encryption_key_version`을 실제로 활용해, 서비스 중단 없이 기존
암호문을 새 키로 재암호화하는 단계적(staged) 로테이션 절차를 설계한다. 평문 자격
증명은 어떤 단계에서도 저장·로그·응답에 남기지 않는다.

**비목표.**

- 프로덕션 키 관리 서비스(KMS/Vault 등) 도입은 다루지 않는다. 키 값이 실제로 어디에
  저장되는지(환경변수 vs. 별도 키 관리 경계)는 바뀌지 않는다 — `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md:139-147`가 이미 이 경계를 프로덕션 과제로 남겨 두었다.
- 정해진 주기의 자동 로테이션("90일마다 자동 회전")은 제안하지 않는다. 트리거는 항상
  운영자의 명시적 실행이다(§1 결정 1).
- Toss 어댑터, 연결 검증 흐름, 회원 노출 API/DTO는 바꾸지 않는다.
- 전략·포트폴리오·매매 원장 도메인은 건드리지 않는다.
- `broker_connection_secrets` 레거시 표 드롭은 이 슬라이스의 필수 조건이 아니다(§1 결정 4).

## 4. 설정 계약 — 키 식별자만, 값은 절대 포함하지 않음

이 절은 프로퍼티 **이름과 형태**만 제안한다. 실제 Base64 키 값은 로컬 환경변수 또는
운영 비밀 관리 도구에만 있어야 하며, 이 문서·코드·테스트 어디에도 실제 값을 적지 않는다
(`AGENTS.md:84-85`, `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md:141-147`).

```yaml
# application.yml (제안, 값은 전부 환경변수 참조이며 여기 적힌 것은 이름뿐이다)
tradeguide:
  broker:
    # 기존 단일 키 프로퍼티. 아래 encryption-keys가 비어 있으면 이 값을 버전 1로 취급한다.
    encryption-key: ${BROKER_CREDENTIAL_ENCRYPTION_KEY:}
    # 신규: 버전별 키 목록. 값이 없는 버전은 목록에 아예 올리지 않는다(빈 문자열 금지).
    encryption-keys:
      - version: 1
        value: ${BROKER_CREDENTIAL_ENCRYPTION_KEY_V1:}
      - version: 2
        value: ${BROKER_CREDENTIAL_ENCRYPTION_KEY_V2:}
    # 신규: 새 암호화(encrypt)에 사용할 "현재" 버전. encryption-keys 목록 안의 값이어야 한다.
    encryption-current-version: ${BROKER_CREDENTIAL_ENCRYPTION_CURRENT_VERSION:1}
```

- 환경변수 이름은 버전을 접미사로 붙이는 인덱싱 방식을 제안한다(`_V1`, `_V2`, ...).
  이 프로젝트가 이미 쓰는 `${ENV:default}` 단일 값 주입 관행과 자연스럽게 이어지고,
  YAML 리스트를 지원하지 않는 단순 호스팅 환경에서도 값마다 별도 환경변수로 설정할 수
  있다. YAML 배열 자체를 하나의 환경변수(JSON 문자열 등)로 주입하는 대안은 값 검증이
  더 복잡해지므로 권장하지 않는다.
- `encryption-current-version`이 `encryption-keys`에 없는 버전을 가리키면 **기동을
  거부한다**(현재 `AesGcmBrokerCredentialCipher`가 키 형식 오류에서 `IllegalStateException`으로
  기동을 막는 것과 같은 원칙, `AesGcmBrokerCredentialCipher.java:39-45`).
- 키가 하나도 설정되지 않은 경우의 동작(503, `BrokerConnectionUnavailableException`)은
  그대로 유지한다(`AesGcmBrokerCredentialCipher.java:100-106`, `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md:258-262`).
- 이 계약의 정확한 프로퍼티 이름은 §1 결정 2로 승인받는다. 위 이름은 초안이다.

## 5. 사전 점검 (Preflight)

로테이션 실행 진입점은 실제 재암호화를 시작하기 전에 다음을 **전부** 확인하고, 하나라도
실패하면 아무 행도 건드리지 않고 종료한다.

1. **설정 완전성.** `encryption-current-version`이 가리키는 키가 `encryption-keys`에
   존재하는가. 이번 로테이션의 목표 버전(파라미터로 받음)도 목록에 존재하는가.
   목표 버전이 `encryption-current-version`과 다르면 명확히 거부한다 — 로테이션은
   "현재 버전으로" 재암호화하는 것이지 임의 버전으로 재암호화하는 도구가 아니다.
2. **원본 버전 복호화 가능성.** 실제로 재암호화가 필요한 기존 버전(들)의 키도
   `encryption-keys`에 남아 있는가. 없으면 그 버전의 행은 복호화조차 할 수 없으므로
   즉시 중단한다 — "쓰기만 새 키로, 읽기는 실패"라는 절반 상태를 만들지 않는다.
3. **중복 실행 방지.** `broker_key_rotation_runs`(§10)에 `status = IN_PROGRESS`인 행이
   이미 있는가. 있으면 즉시 거부한다(§6.3의 부분 유니크 인덱스가 DB 레벨에서 같은
   규칙을 한 번 더 강제한다).
4. **작업량 산정.** 대상 표(`broker_connection_secret_values`, `broker_accounts`)에서
   목표 버전이 아닌 행 수를 각각 센다. 이 값을 실행 기록의 `총 대상 건수`로 남겨,
   완료 시점에 "처리 + 스킵 + 실패 = 총 대상"이 맞는지 검증할 수 있게 한다.
5. **표본 왕복 검증.** 대상 행 중 임의 1건(또는 설정 가능한 소수)을 실제로
   복호화 → (같은 평문으로) 재암호화 → 다시 복호화해 원래 평문과 일치하는지
   확인한다. 이 표본은 어떤 로그·예외 메시지에도 값 자체를 남기지 않고 "일치/불일치"
   불리언만 기록한다. 대량 처리 전에 다중 키 암호기가 실제로 옳게 동작하는지
   확인하는 목적이다.

셋 중 하나라도 실패하면 §10의 실행 기록을 `status = FAILED_PREFLIGHT`로 남기고
사유 코드만 기록한다(원인 예외 메시지에 값이 없는지 §8에서 다시 확인).

## 6. 실행 설계 — 트랜잭션·재시도·멱등성·동시성

### 6.1 단계

1. **RUN 생성.** §10 표에 `status = IN_PROGRESS` 행 하나를 만든다(별도 트랜잭션,
   커밋 즉시). 이 행 자체가 §6.3의 상호 배제 잠금 역할을 한다.
2. **값 단위 재암호화.** `broker_connection_secret_values`를 `id` 오름차순으로
   `encryption_key_version <> targetVersion`인 행만 페이지(§1 결정 3의 배치 크기)
   단위로 조회한다. 각 배치는 독립된 트랜잭션이며, 행마다:
   - 저장된 `encryption_key_version`으로 복호화한다.
   - `encryption-current-version` 키로 재암호화해 새 ciphertext/iv를 만든다(평문은
     바뀌지 않는다 — 로테이션은 "같은 값, 다른 봉투"다).
   - **조건부 UPDATE**로 반영한다:
     `UPDATE broker_connection_secret_values SET ciphertext=?, initialization_vector=?, encryption_key_version=:target WHERE id=:id AND encryption_key_version=:versionReadJustNow`.
     영향받은 행이 0건이면(다른 트랜잭션이 그사이 이 행을 바꿨다는 뜻 — 예: 사용자가
     자격 증명을 교체) **건너뛰고 스킵으로 집계**한다. 실패로 취급하지 않는다: 그
     행은 사용자의 최신 쓰기 경로(`replaceSecretValues`)를 통해 이미 새 값·버전으로
     저장됐을 가능성이 높고, 다음 로테이션 실행에서 다시 대상이 되면 그때 처리하면
     된다.
3. **계좌 일련번호 재암호화.** `broker_accounts`에 같은 방식을 적용하되, 갱신 대상은
   `encrypted_account_sequence`/`account_sequence_initialization_vector`/
   `encryption_key_version` 세 컬럼뿐이다. `status`/`detached_at`/`masked_account_number`는
   조건부 UPDATE의 SET 절에 포함하지 않는다 — `DETACHED` 계좌가 로테이션만으로
   `ACTIVE`로 되돌아가는 §2.4의 부작용을 원천적으로 막는다.
4. **완료 판정.** 두 표 모두 목표 버전이 아닌 행이 0건이 될 때까지 배치를 반복한다.
   모든 배치가 끝나면 RUN 행을 `status = COMPLETED`로 갱신하고 처리/스킵/실패 건수를
   기록한다. 스킵이 0보다 크면 완료는 하되 `partiallySkipped = true`로 표시해 운영자가
   "누락 없이 다음 회차에 다시 훑어야 하는 행이 있었다"는 사실을 알 수 있게 한다.

### 6.2 멱등성

- 조건부 UPDATE의 `WHERE ... AND encryption_key_version = :versionReadJustNow` 절이
  멱등성의 핵심이다. 이미 목표 버전인 행은애초에 조회 대상(`<> targetVersion`)에서
  빠지므로 같은 배치를 몇 번 다시 실행해도 이미 끝난 행은 다시 쓰지 않는다.
- 배치 도중 프로세스가 죽어도(크래시, 배포 중 종료) 이미 커밋된 배치는 그대로 남고,
  아직 처리하지 않은 행은 다음 실행에서 조회 조건에 그대로 다시 걸린다. **자동
  재시작은 하지 않는다** — 운영자가 같은 RUN을 이어서 실행할지, `ABORTED`로 종료할지
  명시적으로 선택한다(§7). 무인 상태에서 조용히 재시도하는 백그라운드 잡을 만들지
  않는 이유는, 실패 원인(예: 키 설정 오류)이 해소되지 않은 채 반복 실행되면 §5의
  사전 점검을 매번 다시 통과해야 하고, 그 반복 자체가 운영 신호를 무디게 만들기
  때문이다.

### 6.3 동시성

- **로테이션 실행 간 상호 배제.** `broker_key_rotation_runs`에
  `status = 'IN_PROGRESS'`인 행이 최대 하나만 존재하도록 PostgreSQL의 부분 유니크
  인덱스로 강제한다(`CREATE UNIQUE INDEX ... ON broker_key_rotation_runs (status) WHERE status = 'IN_PROGRESS'`).
  `BrokerDuplicateCallGuard`의 인메모리 `ConcurrentHashMap`(§2.4)과 달리 이 방식은
  다중 인스턴스 배포에서도 안전하다 — DB가 유일한 진실이기 때문이다.
- **로테이션 배치 vs. 사용자 쓰기.** §6.1의 조건부 UPDATE가 유일한 방어선이다. 사용자가
  `createBrokerConnection`으로 자격 증명을 통째로 교체(`replaceSecretValues`)하는 순간
  기존 행은 삭제되고 새 행이 생기므로(§2.4), 로테이션 배치가 들고 있던 옛 `id`/버전
  스냅샷은 자연히 무효가 되어 조건부 UPDATE가 0건으로 실패 → 스킵 처리된다. 데이터
  손상이 아니라 "누가 늦게 쓰든 마지막 쓰기가 이긴다"는 일반적 낙관적 갱신 결과다.
- **로테이션 배치 vs. 계좌 재검증.** `BrokerConnection.reconcileVerifiedAccounts(...)`가
  기존 계좌를 갱신할 때도 같은 원리로 보호된다: 재검증이 `refreshFromProvider`로
  ciphertext/버전을 이미 최신 키로 다시 쓴 뒤라면, 로테이션 배치의 조건부 UPDATE는
  버전 불일치로 스킵한다.

### 6.4 재시도

- 배치 단위 재시도는 "같은 RUN으로 다시 실행"과 동일하다(§6.2). 별도의 재시도 카운터나
  지수 백오프는 이 슬라이스에 두지 않는다 — 실패의 대부분은 설정 문제(§5)이거나 동시
  쓰기 경합(§6.3, 스킵으로 정상 처리)이라서, 자동 재시도가 아니라 사전 점검 강화가
  더 맞는 대응이다.
- 배치 하나 안에서 개별 행 처리 중 예외(예: 복호화 실패로 §5의 표본 검증을 통과하지
  못한 다른 손상 행 발견)가 나면, 그 배치 트랜잭션 전체를 롤백하지 않고 **그 행만**
  실패로 기록한 뒤 나머지 행은 계속 처리한다(한 손상 행이 배치 전체를 막지 않도록).

## 7. 롤백과 복구

- 로테이션은 평문을 바꾸지 않고 봉투(암호문+IV+버전)만 바꾸므로, "롤백"은 데이터
  복원이 아니라 **이전 버전을 목표로 다시 로테이션을 실행하는 것**과 같다. 단, 이전
  키가 §1 결정 5에 따라 설정에서 아직 제거되지 않았을 때만 가능하다 — 이것이 폐기
  키를 즉시 삭제하지 않는 이유다.
- RUN이 `IN_PROGRESS`인 채 중단된 경우 자동 재개는 하지 않는다(§6.2). 운영자는 RUN을
  이어서 실행하거나 `ABORTED`로 표시해야 다음 RUN을 새로 시작할 수 있다(§6.3의 부분
  유니크 인덱스가 `IN_PROGRESS` 상태가 아니면 더 이상 막지 않는다).
- 복구 검증은 §5의 사전 점검 5번(표본 왕복 검증)을 완료 후에도 한 번 더 수행하는 것을
  권장한다 — 완료 보고서의 건수만 믿지 않고, 임의 표본이 실제로 새 버전 키로
  복호화되는지 확인한다.
- 사고 대응 중 로테이션 이력 자체가 필요하면 §10의 RUN 표를 조회한다. 이 표는
  ciphertext/키 값을 담지 않으므로 그대로 인시던트 문서에 인용해도 안전하다.

## 8. 감사·로그 리덕션

- 새 DB 표 `broker_key_rotation_runs`(§10)가 유일한 감사 기록이다. 이 프로젝트에는
  애초에 로그 프레임워크가 없으므로(§2.4) 로테이션만을 위해 로깅을 처음 도입하지
  않는다 — `broker_reconciliation_runs`와 같은 "실행 기록 행" 관행을 그대로 따른다.
- 기록 컬럼은 provider·시각·건수·상태만 남긴다: `target_key_version`,
  `source_key_versions_observed`(관측된 구버전 목록, 정수 배열이나 CSV),
  `started_at`/`completed_at`, `status`, `secret_values_total/processed/skipped/failed`,
  `accounts_total/processed/skipped/failed`, `triggered_by`(운영자 식별자 또는
  `SYSTEM` 마커 — 회원 행을 참조하지 않는다, 이는 회원 행위가 아니라 운영 행위다),
  `failure_reason`(고정된 사유 코드 문자열, 원본 예외 메시지 아님).
- 어떤 예외 메시지에도 ciphertext·평문·IV·키 값을 담지 않는다. 기존
  `AesGcmBrokerCredentialCipher`의 예외 메시지들이 이미 이 원칙을 지키고 있고
  (`AesGcmBrokerCredentialCipher.java:59, 78, 96, 103`), 로테이션 관련 신규 예외도
  같은 원칙을 그대로 따른다.
- 만약 향후 이 프로젝트에 구조적 로깅이 도입되더라도, 로테이션 이벤트는
  `BrokerCredentials.toString()`이 키 이름만 노출하는 것과 같은 원칙으로 `field_key`
  (이미 비밀이 아닌 고정 식별자, 예: `clientId`)와 버전 정수, 결과 상태만 남기고
  값은 절대 남기지 않는다.

## 9. 관리자/사용자 경계

- 회원에게는 키 버전, 로테이션 상태, 로테이션 이력 중 어떤 것도 노출하지 않는다.
  `BrokerConnectionResponse`, `BrokerHoldingPreviewItemResponse` 등 기존 회원용 DTO에
  키 버전 필드를 추가하지 않는다.
- `/api/me/**` 아래에는 로테이션 관련 엔드포인트를 두지 않는다.
- `/api/admin/**` 아래에도 이번 슬라이스에서는 두지 않는다. `docs/PROJECT_CONTEXT.md:110`이
  명시하듯 인증 활성화 상태에서 관리자 API 경로 자체가 역할 모델 부재로 차단되어 있고,
  `AssetProfileAdminController`가 유일한 기존 관리자 컨트롤러이지만 이 역시 같은
  차단 대상이다. 역할 기반 관리자 인가 모델이 먼저 설계·승인되기 전에 새 관리자
  HTTP 표면을 여는 것은 §1 결정 1에서 이미 보류하기로 했다.
- 대신 §1 결정 1에 따라 운영자가 배포 파이프라인 또는 로컬 셸에서 직접 실행하는
  진입점(Gradle 태스크 또는 `tradeguide.broker.rotation.run-on-startup`처럼 명시적
  옵트인 프로퍼티로 게이팅한 `CommandLineRunner`, `LocalDevelopmentMemberInitializer`가
  `@Profile("local")`로 게이팅된 것과 같은 원칙)를 사용한다. 이 진입점은 애플리케이션
  DB 자격 증명과 §4의 키 설정에 직접 접근할 수 있는 운영자만 실행할 수 있어야 하며,
  브라우저에서 도달 가능한 어떤 경로로도 노출하지 않는다.

## 10. 마이그레이션 필요성

- **불필요:** `broker_connection_secret_values`, `broker_accounts` 자체의 컬럼 변경.
  두 표 모두 이미 값/계좌 단위 `encryption_key_version`을 갖고 있다(§2.1).
- **필요:** 로테이션 실행 기록용 신규 표. 저장소의 다음 미사용 버전 번호로 추가한다
  (이 문서 작성 시점 최신은 V18이므로 초안은 V19이며, 실제 구현 시점에 다른
  마이그레이션이 먼저 병합됐다면 그 다음 번호를 쓴다).

  ```sql
  -- V19__create_broker_key_rotation_runs.sql (초안, 승인 후 확정)
  CREATE TABLE broker_key_rotation_runs (
      id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
      target_key_version INTEGER NOT NULL,
      status VARCHAR(20) NOT NULL,
      secret_values_total INTEGER NOT NULL,
      secret_values_processed INTEGER NOT NULL DEFAULT 0,
      secret_values_skipped INTEGER NOT NULL DEFAULT 0,
      secret_values_failed INTEGER NOT NULL DEFAULT 0,
      accounts_total INTEGER NOT NULL,
      accounts_processed INTEGER NOT NULL DEFAULT 0,
      accounts_skipped INTEGER NOT NULL DEFAULT 0,
      accounts_failed INTEGER NOT NULL DEFAULT 0,
      triggered_by VARCHAR(100) NOT NULL,
      started_at TIMESTAMP(6) NOT NULL,
      completed_at TIMESTAMP(6),
      failure_reason VARCHAR(255)
  );

  -- 동시에 두 실행이 진행 중일 수 없다는 규칙을 DB가 강제한다. 인메모리 가드가 아니라
  -- 이 부분 유니크 인덱스가 다중 인스턴스 배포에서도 유일한 진실이다(§6.3).
  CREATE UNIQUE INDEX uk_broker_key_rotation_runs_single_in_progress
      ON broker_key_rotation_runs (status)
      WHERE status = 'IN_PROGRESS';
  ```

- **결정 대기(§1-4):** 레거시 `broker_connection_secrets` 표를 드롭하는 별도 마이그레이션
  (`DROP TABLE broker_connection_secrets;`). 이 문서의 로테이션 로직은 이 표를 참조하지
  않으므로 로테이션 구현의 선행 조건은 아니다.
- 이 마이그레이션은 `PostgresFlywaySchemaIntegrationTest`가 이미 검증하는 "모든 마이그레이션이
  실제 PostgreSQL에 깨끗이 적용되고 JPA 매핑이 검증되는지"의 대상에 자동으로 포함된다
  (`src/test/java/com/tradeguide/migration/PostgresFlywaySchemaIntegrationTest.java`).

## 11. PostgreSQL 테스트 전략

기존 `src/test/java/com/tradeguide/migration/Postgres*IntegrationTest.java` 계열과
`@Tag("postgres")` + Testcontainers 관행을 그대로 따른다. 기본 `./gradlew test`에서는
제외되고 `./gradlew postgresIntegrationTest`로만 실행된다(`build.gradle.kts:42, 50-56`).
모든 픽스처는 `PostgresBrokerCredentialBackfillIntegrationTest`처럼 더미 문자열만
쓰고 실제 자격 증명이나 실제 키를 절대 쓰지 않는다. 단위 테스트의 키 픽스처도
`AesGcmBrokerCredentialCipherTest.testKey()`처럼 `Base64.getEncoder().encodeToString(new byte[32])`
같은 결정적 더미 값을 쓴다.

| # | 시나리오 | 방식 |
| --- | --- | --- |
| 1 | 재암호화가 평문을 보존한다 | 더미 평문을 버전 1 키로 암호화해 행을 심고, 로테이션 실행 후 그 행이 버전 2로 바뀌었는지, 버전 2 키로 복호화한 결과가 원래 평문과 같은지 확인한다. |
| 2 | 멱등성 | 같은 로테이션을 연달아 두 번 실행한다. 두 번째 실행은 `processed = 0`, 대상 행 0건으로 완료해야 한다. |
| 3 | 동시 사용자 쓰기와의 경합 | 로테이션 배치가 읽은 뒤, 조건부 UPDATE 전에 같은 행을 `replaceSecretValues` 경로로 교체한다(스레드/트랜잭션 순서를 명시적으로 제어 — 타이밍에 의존하는 sleep 방식이 아니라 두 트랜잭션을 순서대로 커밋시켜 재현). 로테이션 쪽은 스킵으로 집계되고, 최종 데이터는 사용자의 새 값만 남아야 한다. |
| 4 | 부분 실패 후 재개 | 배치 크기를 작게 주고 첫 배치만 실행한 뒤 중단, 두 번째 호출로 나머지를 처리한다. RUN 기록의 건수 합계가 §5에서 산정한 총 대상 건수와 일치해야 한다. |
| 5 | 실행 잠금 | RUN을 `IN_PROGRESS`로 만든 채 두 번째 로테이션 시작을 시도해 §10의 부분 유니크 인덱스 위반(`DataIntegrityViolationException`, 기존 `PostgresBrokerHoldingSnapshotTransactionIntegrationTest`가 같은 예외 타입을 이미 사용) 또는 이에 매핑된 애플리케이션 예외로 거부되는지 확인한다. |
| 6 | 계좌 상태 보존 | `DETACHED` 계좌를 로테이션한 뒤에도 `status`가 여전히 `DETACHED`이고 `detached_at`이 바뀌지 않았는지 확인한다(§6.1-3의 SET 절 제한 검증). |
| 7 | 스키마 검증 | 신규 마이그레이션이 `PostgresFlywaySchemaIntegrationTest`의 전체 적용·엔티티 매핑 검증을 통과하는지 확인한다(신규 엔티티를 추가했다면 그 매핑 포함). |

## 12. 구현 체크리스트 (순서대로)

1. `BrokerCredentialCipher`/`AesGcmBrokerCredentialCipher`를 버전→키 맵 + "현재 버전"
   구조로 바꾼다. `decrypt()`가 `EncryptedBrokerCredential.keyVersion()`으로 키를
   선택하도록 고친다. `encrypt()`는 항상 현재 버전을 찍는다.
   `AesGcmBrokerCredentialCipherTest`에 다중 키 암·복호화, 미지정 버전 거부 케이스를
   추가한다.
2. §1 결정 2가 승인되면 `application.yml`에 §4의 설정 계약을 반영한다(값은 여전히
   전부 환경변수 참조). `application-local.yml`/`application-postgres.yml`은 각
   PC의 로컬 상태이므로 이 작업에서 실제 값을 채우지 않는다.
3. `BrokerConnectionSecretValue`에 ciphertext/iv/버전만 바꾸는 좁은 메서드(예:
   `rotateEncryption(String ciphertext, String iv, int keyVersion)`)를 추가한다.
   `BrokerAccount`에도 status/masked 필드를 건드리지 않는 동급 메서드를 추가한다.
4. 신규 레포지토리(`BrokerConnectionSecretValueRepository`, 그리고 계좌용 레포지토리
   또는 JPQL 벌크 쿼리)를 추가해 목표 버전이 아닌 행을 `id` 기준으로 페이지 조회하고,
   §6.1의 조건부 UPDATE를 수행한다.
5. §10의 `V19__create_broker_key_rotation_runs.sql`(또는 실제 다음 버전 번호)을
   추가한다.
6. `BrokerKeyRotationRun` 엔티티/레포지토리를 `BrokerReconciliationRun`과 같은 형태로
   추가한다.
7. `BrokerKeyRotationService`(가칭)를 구현한다: §5 사전 점검 → RUN 생성(§6.3 잠금) →
   §6.1 배치 반복 → §6.1-4 완료/실패 판정.
8. §1 결정 1이 승인한 트리거만 연결한다(이번 슬라이스에는 HTTP 엔드포인트를 추가하지
   않는 것이 기본 권장안).
9. 단위 테스트: 암호기, 엔티티 메서드, 레포지토리 조건부 UPDATE(H2로 가능한 범위).
10. §11의 7개 시나리오를 `postgres` 태그 통합 테스트로 추가한다.
11. `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`의 "운영: ... 복호화 후 재암호화로
    로테이션한다"는 서술을 실제 메커니즘으로 구체화하고, §2.3에서 확인한 "V18에서
    드롭" 오기를 사실에 맞게 정정한다(§1 결정 4의 실제 정리 시점과 무관하게, 최소한
    "아직 드롭되지 않았다"는 사실은 바로잡는다).
12. 수동 검증: Docker로 `./gradlew postgresIntegrationTest` 실행, 더미 다중 버전 행을
    심은 임시 Postgres에서 로테이션 실행, 실행 중 어떤 로그·응답에도 평문/암호문이
    노출되지 않는지 확인.

## 13. 비목표 재확인

- 이 문서는 §4의 설정 계약과 §10의 스키마 초안을 포함해 **어떤 실제 값도 담지 않는다.**
  `application-local.yml`, `.env`, 데이터베이스 값, 각 증권사 제공자 호출은 이 작업에서
  전혀 읽거나 조회하지 않았다.
- 코드·설정·마이그레이션 파일은 이 작업에서 변경하지 않았다. 이 문서가 곧 정책 채택이
  아니며, §1의 다섯 결정이 승인된 뒤 §12 순서로 별도 구현 작업 계약을 통해 진행한다.
