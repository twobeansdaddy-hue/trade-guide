# 증권사 주문 이력 - 의심 항목 재판정 (S8)

- Owner: Claude backend implementation
- Work mode: scoped implementation (백엔드 전용, 프론트엔드·의존성 변경 없음)
- 닫는 갭: `docs/BROKER_IMPORT_NEXT_SLICE_REVIEW.md` B4
- 상태: 구현 완료, 사용자 수락 전

## 문제

`BrokerOrderStagingClassifier`는 애매한 주문을 자동으로 고르지 않고 `MANUAL_OVERLAP_SUSPECTED`(수기 매매와 같아 보임) 또는 `DUPLICATE_SUSPECTED`(식별자는 다른데 내용 지문이 같음)로 남긴다. 승인 경로는 `STAGED`만 반영하므로, 실제로는 별개 거래인 주문을 사용자가 살릴 방법이 없었다.

## 정책

1. 스테이징 항목(`broker_order_import_items`)은 바꾸지 않는다. 사용자 판단은 별도 감사 표 `broker_order_import_item_overrides`에만 남긴다.
2. 재판정할 수 있는 상태는 `MANUAL_OVERLAP_SUSPECTED`, `DUPLICATE_SUSPECTED` 두 가지뿐이다. 체결 없음·값 누락·이미 반영 같은 제외는 사람 판단의 문제가 아니므로 열지 않는다.
3. 결정은 두 가지다.
   - `ALLOW_LEDGER_WRITE`(반영 허용): 결과 상태 `STAGED`
   - `KEEP_EXCLUDED`(제외 유지): 결과 상태는 원래 의심 상태 그대로
   새 스테이징 상태 값을 만들지 않는다. `STAGED`는 기존 의미 그대로 "반영 후보"일 뿐이다.
4. **유효 상태** = 재판정이 있으면 결과 상태, 없으면 분류기 상태. 유효 상태가 `STAGED`인 항목만 승인 후보다.
5. 재판정 자체는 원장을 바꾸지 않는다. 반영은 기존 실행 승인(`POST .../{runId}/approval`)에서만 일어나며, 반영 허용 항목도 대조 불일치 거부, 개시 잔고 기준 시각 필터, 불완전 이력 확인, 이미 반영 멱등, 원장 반영 가능 시장, **승인 직전 전체 원장 재생 검증**을 똑같이 지난다.
6. 재판정하지 않은 의심 항목은 승인을 막지 않는다. 반영 후보가 아니므로 반영되지 않을 뿐이다.
7. 항목 하나에 재판정은 한 건뿐이며 바꾸지 않는다. 같은 결정의 재요청은 기존 기록을 돌려주고(사유도 최초 값 유지), 다른 결정은 409다. 결정을 바꾸려면 주문 이력을 다시 가져와 새 실행의 항목에서 판단한다.
8. 승인 취소(`DELETE .../{runId}/approval`)는 원장 행을 지우고 링크를 `REVOKED`로 바꾸지만, 재판정 행과 링크의 `override_id` 참조는 그대로 남는다.
9. 증권사 API를 호출하지 않고 주문을 보내지 않는다. 응답·오류에 계좌 식별값·자격 증명·토큰을 담지 않으며 사유를 로그에 쓰지 않는다.

## API 계약 (추가 경로만, 기존 계약 하위 호환)

기준 경로: `/api/members/{memberId}/portfolios/{portfolioId}/broker-order-imports/{runId}`

### `POST .../items/{itemId}/override`

요청:

```json
{ "decision": "ALLOW_LEDGER_WRITE", "reason": "증권사 화면에서 수기 기록과 별개 체결로 확인" }
```

응답 `201 Created`(새 기록) 또는 `200 OK`(같은 결정이 이미 있음):

```json
{
  "id": 30,
  "runId": 7,
  "itemId": 102,
  "externalOrderId": "order-2",
  "originalStagingStatus": "MANUAL_OVERLAP_SUSPECTED",
  "originalSkipReasonCode": "MANUAL_OVERLAP",
  "decision": "ALLOW_LEDGER_WRITE",
  "resultingStagingStatus": "STAGED",
  "reason": "증권사 화면에서 수기 기록과 별개 체결로 확인",
  "createdByMemberId": 10,
  "createdAt": "2026-09-08T10:00:00"
}
```

오류:

| 상황 | 상태 | `code` |
| --- | --- | --- |
| `decision` 누락·알 수 없는 값, `reason` 공백·500자 초과 | 400 | — |
| 다른 회원의 경로 | 403 | — |
| 포트폴리오가 없거나 회원 소유가 아님 | 404 | `PORTFOLIO_NOT_FOUND` |
| 실행이 이 포트폴리오에 없음, 항목이 이 실행에 없음 | 404 | — |
| 의심 항목이 아니거나 스테이징이 끝나지 않은 실행 | 409 | `ORDER_IMPORT_ITEM_NOT_OVERRIDABLE` |
| 이미 다른 결정으로 재판정됨 | 409 | `ORDER_IMPORT_OVERRIDE_CONFLICT` |

### `GET .../item-overrides?page&size`

실행 한 건의 재판정 감사 이력을 최신순(`createdAt desc, id desc`)으로 돌려준다. 페이징 계약은 기존 `BrokerHistoryPageResponse`(`items`, `page`, `size`, `totalElements`, `hasNext`, `size` 최대 100)와 같다.

### 기존 응답에 추가된 필드 (이름·의미 변경 없음)

- `GET .../{runId}`, `POST .../broker-order-imports`의 `approval`:
  - `overrideAllowedCount`: 반영 허용으로 반영 후보에 든 의심 항목 수
  - `overrideKeptExcludedCount`: 제외 유지로 재판정된 의심 항목 수
  - `unresolvedSuspectedCount`: 아직 재판정하지 않은 의심 항목 수
  - 관계: `stagedCount + overrideAllowedCount = eligibleCount + baselineExcludedCount`, `suspectedCount = overrideAllowedCount + overrideKeptExcludedCount + unresolvedSuspectedCount`. 재판정이 없으면 기존 값과 동일하다.
- `POST .../{runId}/approval` 응답: `overrideAllowedCount`(반영 후보 중 재판정으로 들어온 수, 기준 시각 필터 전)

## 스키마 (V24)

- `broker_order_import_item_overrides`: 실행·항목·작성 회원 외래키(연쇄 삭제 없음), `UNIQUE(item_id)`, 원래 상태 `CHECK`(의심 두 상태만), 결정·결과 상태 일관성 `CHECK`, 공백 사유 거부 `CHECK`, `(run_id, created_at DESC, id DESC)` 인덱스.
- `broker_order_ledger_links.override_id`: 반영 근거가 된 재판정(nullable 외래키).

## 동시성·멱등

- 같은 항목 동시 재판정: `UNIQUE(item_id)`가 막고, 진 쪽은 새 트랜잭션에서 한 번 재판단한다(같은 결정이면 200, 다르면 409).
- 같은 실행의 승인·재판정: 둘 다 실행 행을 `PESSIMISTIC_WRITE`로 읽어 순서대로 처리한다.
- 승인 재시도: 기존 부분 유니크 인덱스(`uk_broker_order_ledger_links_active_order`)와 1회 재시도 규칙을 그대로 쓴다.

## 검증

- 단위: 재판정 도메인 규칙, 재판정 라이터(소유권·중복·충돌·비의심·빈 사유), 서비스 재시도, 승인 라이터(미재판정 무반영, 허용 반영과 링크 참조, 제외 유지, 허용 항목 재생 실패 무반영), 판정 집계 건수
- 컨트롤러: 201/200, 400(빈 사유·길이·결정 누락·알 수 없는 값), 403, 404, 409 코드, 이력 페이징
- H2 JPA: 유니크 제약, `EXISTS` 집계와 승인 일치, 취소 후 감사 보존, 소유권 거부
- PostgreSQL(`postgresIntegrationTest`, Docker 필요): V24 적용·매핑 validate, `CHECK`/`UNIQUE`, 승인·취소 흐름

## 남은 위험

- 저장된 보유 수량 대조(`reconciliation_status`)는 스테이징 시점에 `STAGED`만으로 계산한 값이다. 식별자가 바뀐 중복 의심 주문 때문에 `MISMATCHED`가 된 실행은 재판정으로 반영 허용해도 대조 불일치로 승인이 막힌다. 새 스냅샷·재조회가 필요하다.
- `DUPLICATE_SUSPECTED`를 반영 허용하면서 짝 주문이 이미 원장에 있으면 매수는 재생 검증을 통과하므로 이중 계상될 수 있다. 판단 근거(짝 주문·수기 기록)를 보여 주는 조회는 이번 범위에 없다.
- 재판정은 바꿀 수 없으므로, 잘못된 결정은 승인 취소 후 해당 실행을 다시 승인하지 않고 새 실행에서 다시 판단하는 방식으로만 바로잡는다.
