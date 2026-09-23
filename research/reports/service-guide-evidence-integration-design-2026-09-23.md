# 장전 가이드 입력 근거의 운영 저장 설계 검토

> **저장소 반영(2026-09-23), 구현으로 대체됨**: 복구본에서 커밋되지 않았던 Codex 설계 문서를 기록용으로 가져왔다. 실제 구현은 이 문서와 이름·값이 다르다. 테이블은 `guide_input_audits`가 아니라 `premarket_guide_input_audits`(V31), 상태는 `INCOMPLETE`가 아니라 `UNVERIFIED`이며, 자산별 캔들 근거(V32·V33)와 포트폴리오 상태 스냅샷(V34, `a5ade8f`)이 추가됐다. 현재 상태의 기준은 `docs/design/GUIDE_INPUT_OBSERVATION.md`와 `docs/design/PORTFOLIO_STATE_SNAPSHOT_PROPOSAL.md`다.

상태: **최소 감사기록 범위 승인 및 구현 진행**. 사용자가 제공자·기록 시각·검증 상태·입력 해시를 별도 테이블에 저장하고, 확보하지 못한 근거는 미검증으로 표시하는 방안을 승인했다. 이 문서는 설계 기준이며 실제 구현과 검증 결과는 Trade Guide 저장소를 기준으로 확인한다.

## 현재 경로와 빠진 근거

| 현재 경로 | 실제로 저장/전달되는 것 | 부족한 것 |
|---|---|---|
| `PremarketGuideService.generateToday` → `PremarketGuideSnapshot` | 기준일, 결과, 생성 시각(`LocalDateTime`), 선택된 캔들 제공자 | 판단에 사용한 각 응답·봉·보유 원장 버전과 이용 가능 시각 |
| `MarketHistoryProvider.getCandles` → `MarketCandle` | 시장, 티커, 거래일(`LocalDate`), OHLCV | 수집 시각, 공급자 원본 타임스탬프 의미, 조정 정책/요청, 세션, 응답 식별자 |
| `TwelveDataMarketHistoryProvider` | `/time_series`의 일봉·주봉. `adjust` 요청 없음 | 공급자 기본 조정값의 명시적 고정, 응답 수신 시각/메타데이터 |
| `TossSecuritiesMarketHistoryProvider` | `adjusted=true` 일봉. 주봉은 일봉 집계. 여러 페이지 가능 | 페이지별 수신 시각·응답 식별자, 조정 의미·집계 계보 |
| `PortfolioBrokerHoldingSnapshot` | 계좌별 읽기 전용 보유 수량과 동기화 시각(`LocalDateTime`) | 가이드가 실제 참조한 원장 상태, 현금, 예약 주문, 계좌별 상태의 동일 시점 보장 |

결론: `generatedAt` 또는 일봉 `tradingDate`를 과거 데이터의 `availableAt`으로 바꿔 넣을 수 없다. 기존 DTO를 확장해 시각만 붙이면 근거가 없는 값을 사실처럼 만들 위험이 있다. 실거래·실제 계좌 정보와 분리된 **감사 상태**로 먼저 도입해야 한다.

## 단계별 권장 모델

**1단계: 최소 감사 기록(권장).** `premarket_guide_snapshots.id`에 연결되는 별도 `guide_input_audits` 테이블을 둔다. `guide_id`(유일), `recorded_at_utc`(Instant), `evidence_status`(`INCOMPLETE`/`CAPTURED`/`VERIFIED`), `missing_reasons`(안전한 코드 목록), `candle_provider`, `adjustment_request`, `response_received_at_utc`(nullable), `market_data_digest`(nullable), `ledger_revision`(nullable), `broker_snapshot_id`(nullable), `portfolio_state_digest`(nullable), `trace_schema_version`을 보존한다. 계좌번호·비밀키·원본 응답·보유 수량 전체는 이 테이블에 넣지 않는다. `CAPTURED`는 기록 존재만 뜻하며 `VERIFIED`는 별도 검증 통과 전에는 설정하지 않는다. 근거 없는 기존 가이드는 `INCOMPLETE` 또는 감사행 부재로 구분하며, 백필 시각을 과거로 위조하지 않는다.

**2단계: 실제 입력 계보.** 제공자 응답/페이지별 수신 로그와 요청 조정 옵션을 안전하게 저장하고, `MarketCandle`과 별도의 `ObservedCandleBatch`를 만들어 캔들 목록 + 출처 + UTC 수신 시각을 반환한다. 토스의 주봉 집계는 원천 일봉 배치/페이지 ID들을 참조한다. 기술·기본·거시·심리 지표가 실제 사용되면 지표별 원본 관측 시각, 발표/빈티지, 수신 시각, 변환 버전을 연결한다. 사용자 상태는 원장 revision과 계좌별 스냅샷 ID를 가이드에 고정한다. 현금·예약 주문의 신뢰할 수 있는 스냅샷이 없으면 수량 가이드를 `검증 불가`로 둔다.

**3단계: 재생·성과 검증.** 연구 `decision_trace`/`replay_gate` 계약과 실제 로그를 대조한다. 시점 검사를 통과한 판단만 미사용 평가 구간으로 보내고, 당시 명목 가격·세션·비용·미체결을 따로 검증한다. 수익성이나 주문 가능성을 감사 상태만으로 인증하지 않는다.

## 구현·검증 경계

- DB 변경은 additive migration + 엔티티/리포지토리 + `PremarketGuideService` 연결 + 집중 테스트로 묶는다. `force=true`로 당일 가이드를 재생성할 때 감사행도 같은 트랜잭션에서 새 버전으로 갱신해야 한다. 기존 고유키 `(portfolio_id, guide_date)`를 변경하지 않는다.
- 인증된 `memberId`·`portfolioId` 접근 경계를 유지한다. 감사 원본이나 포트폴리오 내용은 API에 무조건 노출하지 않는다. 수신 로그·해시에 API 키/토큰/계좌번호가 포함되지 않도록 테스트한다.
- `Instant`와 저장 시간대 정책을 명시하고 `LocalDateTime`을 UTC라고 암묵적으로 해석하지 않는다. 복수 제공자/페이지 응답에서는 단일 수신 시각 필드가 대표값일 뿐이며 상세 계보는 2단계에서 보존한다.
- 테스트: 최초 생성/재호출/강제 재생성/부분 실패/제공자 오류/근거 누락/다른 회원 접근/마이그레이션 호환/비밀값 비노출. `INCOMPLETE`가 UI에서 검증 완료로 보이지 않는지도 확인한다.

이 문서의 1단계 또는 1~2단계 동시 구현 중 어느 쪽을 채택할지는 사용자 결정이 필요하다. 어느 쪽이든 현재 연구 캐시의 과거 수신 시각은 복원되지 않는다.
