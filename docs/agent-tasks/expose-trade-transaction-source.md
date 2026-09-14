# 거래 기록 출처 API 노출

## 담당과 범위

- 담당: Claude backend
- 수정 가능: `src/main/**`, `src/test/**`
- 수정 금지: `frontend/**`, `docs/**`, 의존성 파일, Git 상태, 시크릿

## 목표

`TradeTransaction`에는 이미 `MANUAL`과 `BROKER_OPENING_BALANCE` 출처가 저장된다.
거래 내역 API 응답에도 이 값을 노출해, 화면이 사용자가 직접 등록한 체결과 증권사 보유
종목에서 명시적으로 반영한 개시 잔고를 구분할 수 있게 한다.

## 구현 계약

1. `TradeTransactionResponse`에 `TradeTransactionSource source`를 추가하고, `from()`에서
   엔티티의 source를 그대로 매핑한다.
2. 기존 거래 생성 응답과 목록 응답은 같은 DTO를 사용하므로, 두 API에서 동일하게 source가
   JSON 필드로 노출돼야 한다.
3. 기존 수동 거래는 `MANUAL`, 개시 잔고 반영 거래는 `BROKER_OPENING_BALANCE`가 나와야 한다.
4. 응답 source를 클라이언트 입력으로 받거나, 기존 생성 요청 DTO를 변경하지 않는다.
5. 거래 컨트롤러 API 계약 테스트를 보강한다. 가능하면 서비스 테스트에서 개시 잔고 출처가
   보존되는 기존 검증도 확인한다.

## 검증

- `./gradlew test`
- `./gradlew postgresIntegrationTest`
- 커밋·푸시는 하지 않는다.
