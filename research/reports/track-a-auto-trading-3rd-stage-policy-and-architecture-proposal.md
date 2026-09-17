# 오토매매 3단계 — 정책 문구 제안 + 아키텍처 설계안 (리서치 산출물, 미채택)

> 이 문서는 연구용 제안이다. `docs/AI_COLLABORATION_POLICY.md`에 따라 브로커
> 연동·인증·투자정책 채택은 **사용자 결정 권한**이고, 실제 구현은 Codex 소관이다.
> 이 문서 자체가 `CLAUDE.md`나 `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`를
> 수정하지 않는다 - 리서치 모드 산출물로서 "정책 문구 제안"만 담는다. 사용자가
> 승인하면 아래 제안을 실제 문서 수정과 Codex 작업 계약으로 이어갈 수 있다.

## 0. 왜 예상보다 더 큰 결정인지 (신규 확인)

3단계를 시작하며 `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`를 다시 읽었다.
이 문서에는 오토매매 논의를 시작하기 전에 몰랐던 사실이 있다 - **"주문을 보내지**
**않는다"는 것이 이미 이 프로젝트의 확립된 아키텍처 불변식으로, 문서 안에서**
**두 번 명시적으로 반복된다**:

> "The product never sends orders, conditional orders, or reservation orders
> to a broker." (4번째 줄)
>
> "No order creation, conditional order registration, or automated trade
> action belongs to any step above." (구현 순서 목록 마지막 줄)

즉 오토매매 전환은 `CLAUDE.md` 한 줄을 바꾸는 문제가 아니라, **이미 완성된**
**브로커 연동 아키텍처(암호화된 자격증명 저장, 키 로테이션, 감사 로그, 읽기전용**
**보유내역 임포트)가 처음부터 "절대 주문하지 않는다"를 전제로 설계됐다는 것을**
**뒤집는 결정**이다. 다행히 이 기존 인프라(암호화·키관리·감사로그 패턴)는
주문 실행 기능에도 그대로 재사용 가능한 형태로 잘 설계돼 있다 - 아래 2절에서
이를 확장하는 방향으로 제안한다.

## 1. Toss 주문 API 최종 확인 (공식 OpenAPI 스펙 직접 조회)

2단계 리포트(`track-a-auto-trading-broker-api-feasibility.md`)는 뉴스 보도와
`llms.txt` 요약본 기반이었다. 이번에 `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`
가 인용하는 **공식 OpenAPI 스펙**(`https://openapi.tossinvest.com/openapi-docs/
latest/openapi.json`)을 직접 조회해 재확인했다.

**확인된 실제 엔드포인트**:

| 메서드 | 경로 | 기능 |
| --- | --- | --- |
| POST | `/api/v1/orders` | 주문 생성 |
| PUT | `/api/v1/orders/{orderId}` | 주문 정정 |
| DELETE | `/api/v1/orders/{orderId}` | 주문 취소 |
| GET | `/api/v1/orders` | 주문 목록 조회 |
| GET | `/api/v1/orders/{orderId}` | 주문 상세 조회 |
| GET | `/api/v1/orders/purchasable` | 매수 가능 정보 조회 |

2단계 리포트의 결론(주문 생성/정정/취소/조회, 매수가능 조회 지원)이 공식
스펙으로 재확인됐다. `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`도 이미 이
스펙을 "client-credentials 인증, 계좌 헤더 필요, 허용 IP 제한 문서화"라고
파악하고 있었다(196-202번째 줄) - 다만 그 문서 작성 시점에는 Order 엔드포인트
활용을 명시적으로 전제하지 않았다(당시 목적은 읽기전용 보유내역 조회였다).

## 2. 정책 문구 제안(사용자 승인 시 반영할 초안)

### 2-A. `CLAUDE.md` "프로젝트 원칙" 절

현재:
> Trade Guide는 미국 주식 의사결정 지원 서비스다. 자동 주문이나 수익 보장을
> 제공하지 않는다.

**제안 문구**:
> Trade Guide는 미국·국내 주식 의사결정 지원 서비스다. 기본은 의사결정
> 지원이며, 사용자가 명시적으로 opt-in한 경우에 한해 검증된 전략 규칙의
> 자동 주문 실행을 제공할 수 있다. 자동 주문은 수익을 보장하지 않으며,
> opt-in하지 않은 사용자에게는 어떤 경우에도 자동으로 적용되지 않는다.

### 2-B. `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md` Purpose 절

현재:
> The product never sends orders, conditional orders, or reservation orders
> to a broker.

**제안 문구**:
> The product does not send orders to a broker by default. Order execution
> is an explicit, per-member opt-in capability, gated behind its own consent
> flow, strategy-rule whitelist, and the safeguards in
> `docs/BROKER_ORDER_EXECUTION_ARCHITECTURE.md` (신규 문서, 이 제안이 승인되면
> 작성). A member who has not opted in is served exactly as today -
> read-only guidance only.

이 개정은 "전면 허용"이 아니라 "기본값은 그대로 유지하되 opt-in 경로를 추가"
하는 최소 변경이다 - 기존 읽기전용 사용자 경험은 전혀 바뀌지 않는다는 것을
문구 자체에 명시했다.

## 3. 아키텍처 확장 설계안 (docs/design 범위, 초안)

`docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`의 기존 모델(`BrokerConnection` →
`BrokerConnectionSecretValue`, AES-256-GCM 암호화, 버전별 키 로테이션,
`BrokerSyncRun` 감사로그)을 그대로 재사용하고 확장하는 방향을 제안한다 -
새 암호화·저장 체계를 처음부터 만들 필요가 없다.

### 3-A. 신규 엔티티(제안)

```text
Member
  -> BrokerConnection (기존)
       -> BrokerOrderExecutionGrant (0..1, 신규)
            - strategyId, strategyVersion (어떤 검증된 전략에 대해서만 허용하는지)
            - maxPositionSizePerOrder, maxDailyOrderCount (서킷브레이커 한도)
            - consentedAt, consentVersion (어떤 버전의 동의 문구에 동의했는지)
            - status: ACTIVE | PAUSED | REVOKED
       -> BrokerOrderExecutionRun (0..n, 신규 - 기존 BrokerSyncRun과 동일한
            감사 패턴, 주문 요청/응답의 민감정보 없는 사실만 기록: 전략ID,
            티커, side, 수량, 상태, 타임스탬프, 실패사유코드)
```

- `BrokerOrderExecutionGrant`는 `BrokerConnection`이 이미 가진 "연결"과
  분리된 별도 동의다 - "내 계좌를 읽기전용으로 연결하는 것"과 "이 계좌로
  실제 주문을 내는 것"은 서로 다른 수준의 신뢰를 요구하므로 한 번의 동의로
  묶지 않는다.
- 전략 화이트리스트(`strategyId`)를 두는 이유: 검증되지 않은 임의 규칙이
  자동 주문을 낼 수 없게 하기 위해서다 - `research/STRATEGY_ENGINE_POLICY.md`
  가 이미 "`strategies.json`의 자연어 신호와 리서치 결론은 엔진이 직접
  실행하지 않는다"는 원칙을 갖고 있는데, 이 원칙을 주문 실행 계층에도 그대로
  적용한다.

### 3-B. 서킷브레이커(필수, 협상 불가)

- `maxPositionSizePerOrder`: 사용자가 opt-in 시 직접 설정하는 계좌 대비 최대
  포지션 비율. 시스템 기본 상한(예: 계좌 자산의 20%)을 두고 그 이상은 UI에서
  설정 자체를 거부한다.
- `maxDailyOrderCount`: 하루 최대 주문 건수 제한 - 시스템 오류로 인한 반복
  주문(예: 재시도 루프 버그)을 물리적으로 막는다.
- **킬스위치**: 사용자가 언제든 1클릭으로 `status`를 `PAUSED`로 바꿀 수 있는
  경로가 주문 실행 경로보다 먼저 구현돼야 한다 - "끄는 기능"이 "켜는 기능"
  보다 먼저 완성돼야 한다는 순서를 명시한다.
- 브로커 API 오류(429/5xx)나 예상 밖 응답 시 **기본 동작은 주문 보류**다 -
  실패를 재시도로 덮어 중복 주문을 내는 방향은 금지한다.

### 3-C. 감사·투명성

- `BrokerOrderExecutionRun`은 기존 `BrokerSyncRun`과 동일하게 "민감정보 없는
  사실만" 기록한다(제공자, 시작/종료 시각, 결과, 건수, 정제된 실패코드 -
  자격증명이나 전체 응답 본문은 절대 포함하지 않음).
- 사용자가 언제든 "지금까지 이 시스템이 낸 모든 주문"을 조회할 수 있는 화면이
  필요하다 - 기존 `PortfolioBrokerConnection`/보유내역 임포트 UI 패턴을
  재사용할 수 있다.

### 3-D. 어떤 전략부터 시작하는가

`research/reports/track-a-real-cost-and-tax-revalidation.md`가 세후로도
수익성을 확인한 **Track A 프로덕션 규칙(10주/40주 SMA 골든크로스, 진입
0-4주창) 하나만 1순위 후보**로 제안한다 - 이번 세션에서 검증한 매크로 오버레이
6종은 전부 기각됐으므로 자동 주문 대상에서 제외한다. 여러 전략을 동시에
자동화하지 않고, 가장 오래·가장 많이 검증된 규칙 하나로 먼저 운영해 실제
동작을 확인한 뒤 넓히는 것을 권장한다.

## 4. 규제·법적 확인 필요 사항 (리서치로 답하지 않음, 명시만 함)

다음은 이 리서치가 답할 수 없고, 실제 착수 전 사용자가 별도로 확인해야 하는
항목이다:

1. **개인의 API 기반 자동매매에 대한 자본시장법상 규제**(예: 알고리즘 매매
   신고/등록 의무가 개인 투자자에게도 적용되는지) - 법률 자문 영역이라 이
   리서치는 답하지 않는다.
2. **토스증권 Open API 이용약관**에 자동매매/제3자 서비스 제공 목적 사용에
   대한 제한이 있는지 - 2026-08-13 정식 출시 시점 약관을 직접 확인해야 한다.
3. **한국 거주자의 해외주식 양도소득세 신고 자동화** 여부 - 1단계 검증(22%
   세율)은 세후 수익성 추정이었을 뿐, 실제 신고·납부는 사용자 책임이며 이
   시스템이 자동으로 처리하지 않는다는 것을 사용자에게 명확히 고지해야 한다.

## 5. 다음 단계 제안

1. **사용자가 2절의 정책 문구(2-A, 2-B)를 검토·승인**한다 - 승인되면 그
   문구를 실제로 `CLAUDE.md`/`docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`에
   반영하는 것은 사용자 또는 Codex가 진행한다(Claude 리서치 모드는 이 파일들을
   직접 수정하지 않는다).
2. 승인되면 `docs/agent-tasks/TEMPLATE.md` 양식으로 Codex 작업 계약을 작성해
   3-A~3-C의 아키텍처를 실제로 설계·구현한다 - 이는 "broker APIs... database
   schema changes" 영역이라 `docs/AI_COLLABORATION_POLICY.md`에 따라 Codex와
   사용자가 함께 결정할 범위다.
3. 4절의 규제 확인은 구현과 별개로, 가능한 한 빨리 사용자가 직접(또는 법률
   자문을 통해) 확인해야 한다 - 이게 막히면 나머지 설계가 무의미해진다.

## 출처

- `docs/AI_COLLABORATION_POLICY.md`(에이전트 역할·결정권한 경계)
- `docs/BROKER_AND_PROVIDER_ARCHITECTURE.md`(기존 브로커 연동 아키텍처 - 이번
  제안의 재사용 기반)
- `research/reports/track-a-auto-trading-broker-api-feasibility.md`(2단계,
  뉴스·`llms.txt` 기반 1차 확인)
- `https://openapi.tossinvest.com/openapi-docs/latest/openapi.json`(공식
  OpenAPI 스펙, 2026-09-17 직접 조회로 Order 엔드포인트 재확인)
- `research/reports/track-a-real-cost-and-tax-revalidation.md`(1단계, 세후
  수익성 확인)
- `research/STRATEGY_ENGINE_POLICY.md`(전략 화이트리스트 원칙의 기존 근거)
