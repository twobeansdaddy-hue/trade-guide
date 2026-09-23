# 조건부 가격·수량 계산 코어 v1 실행 보고서

작성일: 2026-09-22
작업 계약: `docs/agent-tasks/session-plan-core-v1.md`
성격: 합성 입력 기반 오프라인 연구용 산식. 운영 코드·정책·DB·API·주문 경로와 연결하지 않는다.

## 1. 이 코어가 아닌 것

**가격 예측기, 채택된 전략, 추천, 실행 가능한 브로커 주문이 아니다.** 가격·수량·한도·수수료는 전부 호출자가 명시적으로 넣는다. 기본값(손절 비율, 수량 비율 등)을 만들지 않으며, 한도가 없으면 기본값 대신 `MISSING_FIELD`로 차단한다. 기존 `TradePlanPreviewService`는 읽기 전용 참고일 뿐 대체하지 않는다. 테스트는 산술 단위 검증이며 시장·백테스트·수익성 근거가 아니다.

## 2. 산출물

| 경로 | 내용 |
|---|---|
| `research/scripts/session-plan-core/plan_core.py` | `build_plan(input)`, `apply_fill(state, plan, fill)` 순수 함수 + 최소 CLI. 표준 라이브러리 `Decimal`만 사용 |
| `research/scripts/session-plan-core/test_plan_core.py` | 합성 unittest 46개 |
| `research/scripts/session-plan-core/examples/usd_three_scenarios.json` | USD 세 시나리오 예시 |
| `research/scripts/session-plan-core/examples/krw_partial_buy_fill.json` | KRW 부분 매수 체결 예시 |
| `research/reports/session-plan-core-v1.md` | 이 문서 |

기존 preflight·운영 코드·전략 정책·의존성·Git index/이력은 변경하지 않았다. 네트워크·설치·API 키·실계좌·커밋·푸시도 없다.

## 3. 실행 방법과 결과

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s research/scripts/session-plan-core -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s research/scripts/session-engine-data -p 'test_*.py'
python3 research/scripts/session-plan-core/plan_core.py build research/scripts/session-plan-core/examples/usd_three_scenarios.json
python3 research/scripts/session-plan-core/plan_core.py fill research/scripts/session-plan-core/examples/krw_partial_buy_fill.json
```

| 검증 | 실제 결과 |
|---|---|
| 신규 `session-plan-core` unittest | **46개 실행, 전부 통과** (계약 최소 25개) |
| 기존 `session-engine-data` preflight 회귀 | **90개 실행, 전부 통과** |
| `__pycache__`·후행 공백 점검 | 생성물·후행 공백 없음 (Git index는 건드리지 않고 grep으로 확인) |

예시 CLI는 성공 시 종료코드 0, 오류는 구조화된 JSON과 종료코드 2를 낸다. lint/build/백엔드 테스트는 이 작업 범위가 아니라 실행하지 않았다.

## 4. 계약 항목별 구현 요약

**입력 검증(fail-closed).** 십진수는 부호·지수·공백 없는 최대 32자 문자열만 허용한다. bool·float·정수·NaN·Infinity·`1e3`·빈 문자열·유니코드 숫자를 거부한다. `version`/`cashScale`은 bool이 아닌 int다. 알 수 없는 필드도 거부해 오타가 조용히 무시되지 않는다. 모든 오류는 `{ok:false, error:{code, field, message}}`이며 메시지에 입력 값을 싣지 않고 입력을 변경하지 않는다. 예상 밖 예외도 `CALCULATION_ERROR`로 바꾼다. 가격은 틱, `stopPrice`는 mark·모든 매수가보다 낮아야 하며 틱을 지켜야 한다. 레벨/체결의 종목·통화가 다르면 거부하고, `USD`/`KRW` 외 통화와 환전은 없다.

**BUY.** 호출자 우선순위대로 요청수량을 현금·노출 여유·모델 위험 예산 세 가지로 차례로 캡한다. 캡은 나눗셈을 내림(`ROUND_FLOOR`) 후 step 내림으로 구하고, 현금 비용은 `ceil(qty·price·(1+fee), cashScale)`로 올림한다. 적용 후 세 한도를 다시 검사해 초과하면 계획을 만들지 않는다(`INVARIANT_VIOLATION`). 노출은 매수 명목가(`qty·price`), 위험은 `qty·(price-stop+(price+stop)·fee)`로 누적 예약한다. 기존 노출은 `held·mark`, 기존 위험은 `held·(mark-stop+stop·fee)`이며, 기존 한도 초과 시 여유는 0이 되어 추가 매수만 막고 매도를 강제하지 않는다. 0주 레벨은 `status=BLOCKED`, `executable=false`, `reasonCodes`(`CASH_LIMIT`/`EXPOSURE_LIMIT`/`RISK_LIMIT`/`BELOW_STEP`)로 명시한다. 매도 대금은 매수 여력에 넣지 않는다.

**SELL / PROTECTIVE.** SELL은 보유수량을 step 내림한 값 안에서 호출자 우선순위로 합계를 캡하고 `stepResidualQuantity`·`unplannedQuantity`·`unallocatedSellableQuantity`로 잔량을 드러낸다. 미래 BUY 수량을 빌리지 않는다. PROTECTIVE는 현재 매도 가능 수량 전부를 `stopPrice`에 놓는 시나리오다. 세 시나리오는 `MUTUALLY_EXCLUSIVE_ALTERNATIVES`이고 `ocoAssumed=false`, 계획·시나리오 모두 `brokerSubmissionReady=false`, `requiresReplanAfterFill=true`, 한국어 비추천 고지문(`notice`)을 항상 출력한다.

**apply_fill.** 양수 계획 레벨에 대한 부분/전체 체결 하나만 받는다. 계획이 입력 전체(state 포함)를 담고 있어, 검증 시 그 입력으로 계획을 다시 만들어 JSON 정규형이 한 글자라도 다르면 `PLAN_TAMPERED`로 거부한다(`False`와 `0`처럼 파이썬 `==`로는 같은 값도 구분). 그다음 종목·통화·**version**(`STALE_STATE_VERSION`)·**전체 상태 내용**(`STATE_MISMATCH`)을 계획과 대조한다. 체결은 명시 실행가·실제 수수료·수량 step을 지켜야 하고, 수량은 계획 수량 이하, 매수가는 지정가 이하, 매도가는 지정가 이상(PROTECTIVE만 갭 하락 허용)이어야 한다. 현금 차감은 올림, 매도 대금은 내림, 순매도대금이 음수면 `NEGATIVE_PROCEEDS`로 거부한다. version을 1 올려 새 상태를 돌려주고 `oldPlanInvalidated=true`, 무효 시나리오 목록을 함께 반환한다.

## 5. 계약에 명시되지 않아 내가 정한 가정 (검토 요청)

1. **입력 형태**: `{"state": {...}, "params": {...}}`. `externalReservationsConfirmed`는 state 안에 두어 상태에 바인딩했다. 반환된 새 state는 그대로 다음 `build_plan`에 넣을 수 있다.
2. **평균단가는 기존 `HoldingCalculator`와 동일하게 매수 수수료를 취득원가에 포함**하고, 소수 10자리에서 `HALF_UP`으로 반올림한다. 매도 시에는 잔량의 평균단가를 유지한다. 이는 기존 원장의 계산 규칙과 일치시키기 위한 것이지 투자 규칙이 아니다.
3. **`availableCash`는 `cashScale` 자릿수 안이어야 한다**(`CASH_SCALE_MISMATCH`). 그래야 올림한 예약이 남은 현금을 넘지 않음이 증명된다.
4. 십진수는 문자열만 허용하며 정수 타입(`5`)도 거부한다. 지수표기·부호 없음도 의도적 보수 선택이다.
5. 각 체결은 새 계획의 레벨에 대해 검증한다. "누적 체결량 ≤ 계획량"은 체결마다 재계획하므로 체결 1건 ≤ 계획량으로 구현된다.
6. `markPrice`에는 틱 검사를 하지 않는다(참고가). 실제 체결가에도 틱 검사를 하지 않는다(가격 개선 허용).
7. 내 구현 중 셸의 `apply_patch`로 파일을 만들었고, 테스트 파일의 소규모 수정 2건은 허용 경로 안에서 Python 문자열 치환으로 했다.

## 6. 한계

- **영속 멱등성·동시 기장 없음.** 같은 체결을 같은 옛 상태에 두 번 적용하면 같은 결과가 나온다. 호출자가 새 version을 원자적으로 저장한 뒤 통합해야 한다. 이 모듈은 상태를 저장하지 않는다.
- **계획 무결성 검사는 변조 탐지이지 인증이 아니다.** 계획은 입력에서 재계산해 대조하므로 위조된 결과값은 잡지만, 입력과 계획을 함께 새로 만든 호출자를 막을 수는 없다.
- **실제 매수 수수료는 현금과 평균단가에 반영한다.** 실제 수수료가 추정보다 커도 모델 위험 예산·노출은 다시 검사하지 않는다.
- **손절은 모델 예산일 뿐**이다. 갭·슬리피지·거래정지로 실제 손실은 더 클 수 있다. OHLC로 체결을 흉내 내거나 손절 체결을 보장하지 않는다.
- 단일 종목·단일 통화만 다룬다. 세금, 환율, 공매도, 호가 잔량, 부분체결 순서, 거래소 캘린더, 주문 정정·취소는 범위 밖이다.
- 위험·노출 산식(mark→stop 손실 + 매도 수수료, 매수 명목가)은 계약이 준 산식이며 검증된 투자 규칙이 아니다. 한도 값은 모두 호출자의 절대 제약이다.
- 테스트는 손계산 산술 검증이므로 전략 유효성, 수익성, 시장 사실성을 뒷받침하지 않는다.

## 7. 복구 후 Codex 독립 검토 (2026-09-23)

Orca 작업 공간 삭제 후 로컬 Claude 작업 기록에서 파일을 복원해 별도 로컬 Git 저장소에 보존했다. 원본 파일의 바이트 단위 동일성은 확인할 수 없지만 복구본에서 테스트를 직접 재실행했다.

경계값 검사에서 `state.version=2^63-1`인 계획의 체결이 성공하여 허용 범위 밖의 새 버전을 반환하는 결함을 재현했다. 이 상태는 다음 `build_plan`에서 거부됐다. `apply_fill`이 체결 전 `STATE_VERSION_EXHAUSTED`로 차단하도록 수정하고 회귀 테스트를 추가했다.

- 계산 코어 unittest: **48개 실행, 48개 통과**.
- 데이터 검사기 회귀 unittest: **90개 실행, 90개 통과**.
- `git diff --check`: 통과.

추가 검토에서 매수 수수료를 제외하고 소수 8자리에서 올리던 평균단가가 기존 보유 원장과 달라지는 것을 확인해 위와 같이 수정하고 회귀 테스트를 추가했다. 이 검토는 입력 산술과 상태 전이를 확인한 것이며 실제 시세의 진위, 체결 가능성, 다종목 자금 배분, 예측 성능, 수익성 또는 운영 주문 안전성을 인증하지 않는다.
