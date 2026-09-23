# 세션 데이터 수집 근거 계약 v1 (연구 전용)

장전·애프터·본장 가이드의 과거 재현에서는 `eventAt`을 **완성된 봉의 종료 시각**, `availableAt`을 **실제 응답 수신 시각**으로 둔다. 공급자의 일봉 날짜나 봉 시작 timestamp를 둘 중 하나로 복사하지 않는다. 이 계약은 기존 `preflight.py`에 선택적으로 적용하는 구조 검증이며 운영 수집기를 연결하거나 시장 사실성을 인증하지 않는다.

## 요청과 입력

`requirements.requireObservedProvenance: true`를 넣으면 아래 근거가 필수다. `strict: false`여도 누락을 WARN으로 낮추지 않는다.

| 위치 | 필드 | 의미 |
|---|---|---|
| manifest | `sourceProvider` | 공급자 식별자 |
| manifest | `sourceAdjustmentMode` | 실제 요청한 조정 옵션: `RAW`, `SPLITS`, `SPLITS_AND_DIVIDENDS` |
| manifest | `adjustmentEvidenceRef` | 요청 옵션을 확인할 수 있는 기록 식별자. 키·토큰은 금지 |
| manifest | `adjustmentPolicy` | 위 요청과 각각 `RAW`, `SPLIT_ADJUSTED`, `TOTAL_RETURN_ADJUSTED`로 일치해야 함 |
| manifest | `availabilityMode` | `ACTUAL_RECEIPT`이어야 함 |
| row | `barStartAt` | 봉 시작 시각(오프셋 포함) |
| row | `eventAt` | 봉 종료 시각. 분봉이면 시작과 interval 차이가 정확히 일치해야 함 |
| row | `sourceTimestampAt`, `sourceTimestampMeaning` | 원본 공급자 시각과 의미(`BAR_OPEN`/`BAR_CLOSE`). 선언한 봉 경계와 일치해야 함 |
| row | `receivedAt`, `availableAt` | 수집기가 기록한 실제 수신 시각. 두 값은 동일해야 하고 의사결정 `cutoffAt` 이하여야 함 |

예를 들어 뉴욕 09:30에 시작해 09:31에 끝난 1분봉이 09:31:02에 수신됐다면 `sourceTimestampMeaning=BAR_OPEN`일 때 공급자 시각은 09:30, `eventAt`은 09:31, `receivedAt=availableAt`은 09:31:02다. 시각은 모두 UTC 오프셋을 포함한다.

## 제한과 적용 순서

- 연구 수집기 `research/scripts/session-engine-data/twelve_capture.py`는 `/time_series` 요청에 `adjust`를 명시하고, 응답 본문을 읽은 직후 UTC 수신 시각과 응답 SHA-256, 비밀키를 제외한 요청 파라미터, 공급자 응답을 함께 보존한다. 결과는 `PENDING_CALENDAR_AND_SESSION_VERIFICATION`으로 남기며 바로 preflight 행이나 주문 가격으로 바꾸지 않는다. `none`/`splits`/`all`은 정규화 단계에서 각각 `RAW`/`SPLITS`/`SPLITS_AND_DIVIDENDS`로 명시적으로 매핑해야 한다.
- 실험 실행 시 키는 `TWELVE_DATA_API_KEY` 환경 변수로만 제공한다. 예: `python3 research/scripts/session-engine-data/twelve_capture.py --symbol SOXL --interval 1day --adjust splits --outputsize 100 --output capture.json`. 출력 파일이 이미 있으면 덮어쓰지 않는다. 실제 API 호출에는 제공자 사용량·요금·라이선스가 적용될 수 있으므로 이 단계의 검증에서는 모의 응답만 사용했고 실호출은 하지 않았다.
- 수집기에서 요청 옵션·응답 수신 시각을 **수집 당시** 기록해야 한다. 이미 내려받은 SOXL 일봉 CSV에는 수신 시각이 없으므로 위 필드를 사후 추정해 채워서는 안 된다.
- `adjustmentEvidenceRef`는 문자열의 존재만 검사한다. 실제 요청·응답과 일치하는지, 수신 시각이 조작되지 않았는지, 라이선스가 적합한지는 별도 증거 검토가 필요하다.
- 일봉의 종료 시각은 단순히 시작+24시간이 아니다. 거래소 일정·휴장·서머타임을 아는 수집기가 제공해야 한다. 이 검사기는 분봉 간격만 검사하고 거래소 캘린더와 세션 경계는 검증하지 않는다.
- 조정된 과거 OHLC로 지표를 연구할 수 있어도 그 가격을 당시 예약 주문 가격으로 사용할 수는 없다. 명목 가격, 기업행사, 호가단위, 유동성 검증은 별도 단계다.
- 따라서 현 캐시는 오프라인 계산 실험에만 쓰고, 실제 세션 가이드의 성능 주장은 보류한다. 국내 주봉 원본도 일중 세션 근거가 아니다.

검증: `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s research/scripts/session-engine-data -p 'test_*.py'`로 102개 통과. 기존 90개 + 수집 근거 6개 + 수집기 모의 응답 6개다.
