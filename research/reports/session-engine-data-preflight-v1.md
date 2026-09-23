# 세션 예측엔진 데이터 준비 검사기 v1 실행 보고서

작성일: 2026-09-21
작업 계약: `docs/agent-tasks/session-engine-data-preflight-v1.md`
성격: 연구용 오프라인 도구. 운영 코드·정책·DB·API·주문 경로와 연결하지 않는다.

## 1. 이 검사기가 인증하지 않는 것

**PASS는 "이 파일이 정의한 JSON 구조·내부 일관성 검사를 통과했다"는 뜻일 뿐이다.** 다음을 인증하지 않는다.

- 시장 사실성, 과거 가격·거래량의 진실성
- 라이선스·이용권 (라이선스 `UNKNOWN`은 WARN이며 운영 승격 보류는 별도 절차다)
- 수익성, 전략 유효성

이 결과 JSON에는 같은 취지의 `notice` 문구가 항상 포함된다.

## 2. 산출물

| 경로 | 내용 |
|---|---|
| `research/scripts/session-engine-data/preflight.py` | `validate(payload)` 순수 함수와 CLI. 표준 라이브러리만 사용 |
| `research/scripts/session-engine-data/test_preflight.py` | 합성 데이터 unittest 90개 |
| `research/scripts/session-engine-data/examples/*.json` | 합성 예시 4개 (PASS, WARN, BLOCKED 2개) |
| `research/reports/session-engine-data-preflight-v1.md` | 이 문서 |

기존 연구 결과·정책·운영 코드는 변경하지 않았고 기존 연구를 재실행하지 않았다. 네트워크, 설치, 모델 학습, 주문 실행은 없다. 예시 데이터는 모두 `SYN-*` 합성 값이며 실제 시세가 아니다.

## 3. 실행 방법

```bash
# 검사 (JSON을 stdout으로. PASS/WARN 종료코드 0, BLOCKED 종료코드 2)
python3 research/scripts/session-engine-data/preflight.py research/scripts/session-engine-data/examples/pass_us_regular_1m.json

# 테스트
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s research/scripts/session-engine-data -p 'test_*.py' -v
```

Python에서는 `import preflight; preflight.validate(payload)`로 호출한다. 입력은 변형하지 않는다.

읽기 실패, 깨진 JSON, 인자 오류도 같은 형태의 구조화된 BLOCKED 결과(`INPUT_UNREADABLE`, `MALFORMED_JSON`, `USAGE_ERROR`)로 낸다. 오류 메시지에는 traceback, 파일 경로, 파일 내용, 입력 값을 싣지 않는다. 검사 중 예기치 못한 예외는 `INTERNAL_ERROR`로 fail closed 한다.

## 4. 입력 예시

```json
{
  "manifest": {
    "datasetId": "synthetic-us-regular-1m-example",
    "market": "US", "currency": "USD", "interval": "1m",
    "timezone": "America/New_York",
    "sessionCoverage": ["REGULAR"],
    "adjustmentPolicy": "RAW",
    "availabilityMode": "ACTUAL_RECEIPT",
    "universeMode": "FIXED_DIAGNOSTIC_SET",
    "licenseStatus": "RESEARCH_ONLY_SYNTHETIC"
  },
  "requirements": {
    "market": "US", "currency": "USD", "interval": "1m", "session": "REGULAR",
    "cutoffAt": "2026-03-10T00:00:00Z",
    "requirePitUniverse": false, "requireVintage": false
  },
  "rows": [
    {"instrumentId": "SYN-A", "market": "US", "currency": "USD", "session": "REGULAR",
     "eventAt": "2026-03-06T09:31:00-05:00", "availableAt": "2026-03-06T09:31:02-05:00",
     "open": 100.0, "high": 100.5, "low": 99.8, "close": 100.2, "volume": 1200}
  ]
}
```

- `eventAt`은 봉 종료 시각이다. 모든 시각은 UTC 오프셋이 필수이며 UTC로 변환해 비교한다. 오프셋 없는 시각과 날짜만 있는 값은 거절한다.
- `vintageAt`은 선택 필드다. `requireVintage`가 true이면 모든 행에 필요하다.
- `requirements.strict`(선택, 기본 true)와 `manifest.vintagePolicy`, `manifest.evidenceRefs`(선택)는 계약 목록에 없는 확장 필드다. 아래 §7 참고.

## 5. 출력 형식

```json
{
  "status": "PASS | WARN | BLOCKED",
  "issues": [{"code": "...", "field": "rows[3].close", "message": "...", "severity": "WARN | BLOCKED"}],
  "issuesTruncated": false,
  "rowCount": 4,
  "instrumentStatus": {"SYN-A": "PASS", "SYN-B": "BLOCKED"},
  "notice": "이 결과는 구조와 내부 일관성만 검사한다 ..."
}
```

- `status`, `issues[{code, field, message}]`, `rowCount`는 계약 그대로다. `severity`, `issuesTruncated`, `instrumentStatus`, `notice`는 추가 필드다.
- `instrumentStatus`는 종목별 판정이다. 한 종목의 행 결함은 그 종목만 BLOCKED로 만들고 정상 종목은 PASS로 남긴다. 반면 manifest/requirements 수준 결함이나 WARN은 모든 종목에 반영한다. 전체 `status`는 어느 하나라도 BLOCKED면 BLOCKED다.
- 이슈 목록은 500개까지만 싣고(`issuesTruncated`), `status`는 전체 이슈로 계산한다.
- 이슈 `field`는 `manifest.currency`, `requirements.cutoffAt`, `rows[3].availableAt` 형태다.

## 6. 판정 규칙

**BLOCKED**

| 코드 | 조건 |
|---|---|
| `INVALID_TYPE`, `MISSING_FIELD`, `INVALID_ENUM`, `INVALID_VALUE`, `ROW_NOT_OBJECT`, `EMPTY_ROWS` | 누락·null·잘못된 타입/열거값·빈 rows. bool을 숫자로, 문자열을 숫자로 받지 않는다 |
| `INTERVAL_MISMATCH` | 요구 interval과 manifest interval이 다름. 일봉/주봉을 분봉으로 추정하지 않는다 |
| `SESSION_COVERAGE_MISSING`, `SESSION_NOT_COVERED`, `SESSION_NOT_DECLARED`, `REQUIRED_SESSION_NO_ROWS`, `INSTRUMENT_REQUIRED_SESSION_NO_ROWS` | 세션 목록 부재, 요구 세션이 선언에 없음, 행의 세션이 선언에 없음, 데이터셋 전체에 요구 세션 행이 0개(전역), 관측된 종목 중 요구 세션 행이 없는 종목(종목별) |
| `MARKET_MISMATCH`, `CURRENCY_MISMATCH` | manifest·requirements·행 사이의 시장/통화 불일치 |
| `ADJUSTMENT_POLICY_UNKNOWN`, `AVAILABILITY_MODE_UNKNOWN`, `AVAILABILITY_FIXED_LAG_APPROXIMATION` | strict일 때 (`strict:false`면 WARN) |
| `PIT_UNIVERSE_REQUIRED` | `requirePitUniverse`인데 `universeMode`가 `POINT_IN_TIME`이 아님 |
| `VINTAGE_MISSING`, `VINTAGE_EVIDENCE_UNDECLARED` | `requireVintage`인데 행에 `vintageAt`이 없음 / manifest에 `vintagePolicy`·`evidenceRefs` 근거가 없음(비어 있는 `evidenceRefs`는 근거가 아님). 둘 중 하나라도 빠지면 차단 |
| `FUTURE_EVENT_AT`, `FUTURE_AVAILABLE_AT`, `FUTURE_VINTAGE_AT` | 각 시각이 `cutoffAt`보다 미래 (정확히 같으면 허용) |
| `AVAILABLE_BEFORE_EVENT` | `availableAt < eventAt` |
| `INVALID_TIMESTAMP`, `NAIVE_TIMESTAMP` | 해석 불가 시각, 오프셋 없는 시각 |
| `NON_POSITIVE_PRICE`, `NEGATIVE_VOLUME`, `NON_FINITE_NUMBER`, `INVALID_OHLC` | 가격 ≤ 0, 거래량 < 0(0은 허용), NaN/무한대/float 범위 초과, high/low 관계 위반 |
| `DUPLICATE_BAR` | 같은 종목·세션·eventAt(UTC 기준) 중복 |
| `OUT_OF_ORDER` | 같은 종목·세션 안에서 eventAt이 UTC 기준 역순 |
| `EVENT_SPACING_BELOW_INTERVAL`, `INTERVAL_LOOKS_DAILY` | 분봉 선언인데 인접 봉 간격이 interval보다 짧음 / 시리즈의 모든 간격(2개 이상)이 20시간 이상 |
| `INTERNAL_ERROR`, `INPUT_UNREADABLE`, `MALFORMED_JSON`, `USAGE_ERROR` | 검사기 자체 오류, CLI 입력 오류 |

**WARN**: `LICENSE_UNKNOWN`, `UNIVERSE_MODE_UNKNOWN`(PIT 미요구 시), `TIMEZONE_UNVERIFIED`, 그리고 `strict:false`로 낮춘 조정·공개시점 이슈.

## 7. 계약 해석과 선택 사항 (확인 필요)

1. **strict의 정의**: 계약에 `strict`라는 requirements 필드가 없다. 그래서 선택 필드 `requirements.strict`를 추가하고 생략 시 `true`(fail closed)로 해석했다. 계약이 다른 뜻으로 의도했다면 조정이 필요하다.
2. **빈티지 "증거"**: `requireVintage`이면 모든 행의 `vintageAt`과 manifest의 `vintagePolicy` 또는 비어 있지 않은 `evidenceRefs`가 모두 있어야 한다. 하나라도 없으면 BLOCKED다(조율자 요청으로 근거 부재를 WARN에서 BLOCKED로 상향). `vintagePolicy`/`evidenceRefs`는 계약 manifest 필드 목록에 없는 선택 필드이므로, `requireVintage`를 쓰는 입력은 이 필드를 함께 채워야 한다.
3. **`licenseStatus`**: 허용값을 임의로 정의하지 않았다. 비어 있지 않은 문자열이면 받고 대소문자 무시 `UNKNOWN`만 WARN이다.
4. **미래 기준**: 벽시계 현재 시각이 아니라 `requirements.cutoffAt`과 비교한다. 실행 시점이 결과에 영향을 주지 않도록 했다.
5. **분봉 간격 휴리스틱**: 계약에 없는 보수적 추가 검사(`EVENT_SPACING_BELOW_INTERVAL`, `INTERVAL_LOOKS_DAILY`)다. 일봉을 분봉 라벨로 붙인 데이터를 잡기 위한 것이며, 봉 사이가 비어 있는 것(누락 봉)은 캘린더 지식이 없어 검사하지 않는다.
6. **`timezone`**: IANA 이름 확인은 WARN 수준이다. 시각 비교는 행의 UTC 오프셋만 쓰며, 오프셋이 선언 시간대의 DST 규칙과 맞는지는 검사하지 않는다.

## 8. 테스트 결과

명령: `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s research/scripts/session-engine-data -p 'test_*.py' -v`

- **90개 실행, 90개 통과, 실패 0** (Python 3.11.9, 약 0.4초)
- 최초 실행에서 1개가 실패했다. 테스트가 기대한 문구와 검사기 메시지 표현이 달라서였고, 메시지를 고친 뒤 재실행해 통과했다.
- 이후 조율자 요청으로 빈티지 근거 부재를 WARN에서 BLOCKED로 바꿨다. 기존 WARN 기대 테스트 1개를 BLOCKED 기대로 교체하고(`vintageAt`만 있는 사례 회귀 테스트), 근거 충족(`evidenceRefs`만), 빈 `evidenceRefs`, 근거만 있고 `vintageAt` 없음 3개를 추가해 총 84개로 재실행했다.
- Post-review correction(§11) 반영 후 회귀 테스트 6개를 추가해 90개로 재실행했고 전부 통과했다.
- `git diff --check`: 종료코드 0. 새 파일이 아직 untracked라 이 명령만으로는 새 파일이 검사되지 않으므로, 임시 intent-to-add 후 재확인하고 바로 되돌렸으며 새 파일의 공백 오류가 없음도 grep으로 확인했다.

커버리지 (결함별 케이스):

- 정상 합성 데이터 PASS, 입력 불변, 경계값(cutoff와 동일, availableAt=eventAt, 거래량 0, 평평한 봉)
- fail closed: 비객체/누락/빈 rows/null/타입 오류, manifest·requirements·행 필드별 누락, 내부 예외
- 일봉·주봉이 분봉 요구를 통과하지 못함, 세션 부재·미선언·요구 세션 행 없음, 일봉 간격의 분봉 라벨
- 조정 UNKNOWN, 공개시점 UNKNOWN/고정지연(strict와 non-strict), 라이선스 UNKNOWN WARN, PIT 종목군, 빈티지
- 미래 eventAt/availableAt/vintageAt, availableAt < eventAt, 오프셋 없는 시각
- **DST**: 3월 DST 시작 전후(-05:00/-04:00) 정렬, 문자열 순서와 UTC 순서가 다른 경우, 오프셋만 다른 같은 시각의 중복, 11월 DST 종료 시 두 번 존재하는 벽시계 시각의 구분, KR(+09:00) 데이터와 US 오프셋 cutoff 비교
- 중복, 비정렬, 다종목 인터리브 정렬, 시리즈별 위반 격리
- 잘못된 OHLC 5종, 비양수 가격, 음수 거래량, NaN/무한대/거대 정수, bool·문자열 숫자
- **요구 세션 종목별 검사**: 일부 종목만 누락(mixed), 전 종목 누락(all-missing), 전 종목 충족(all-covered), Codex 재현 사례, 행 순서 무관, 종목 식별 불가 행
- **부분 실패**: 한 종목만 BLOCKED, 정상 종목은 PASS 유지, manifest 수준 결함은 전 종목 BLOCKED, 여러 결함 동시 보고
- CLI: 예시 4개의 상태와 종료코드, 깨진/빈/깊게 중첩된 JSON, 없는 파일·디렉터리·바이너리, 인자 오류, JSON `NaN` 리터럴, 오류 메시지에 입력 값·경로 미노출

## 9. 확인하지 못한 영역 (이번 범위 밖)

이 검사기와 테스트는 다음을 검사하지 않으며, 검사했다고 주장하지 않는다.

- 실제 거래소 캘린더(휴장일, 세션 시작·종료 시각), 서머타임 전환일의 세션 길이
- 호가단위, 가격 상하한, 분할·배당 계수의 실제 정합성, `adjustmentPolicy` 선언의 진위
- 과거 가격·거래량의 진실성, 실제 접수 시각의 진위, 정정 이력
- 체결 순서(동일 봉 내 고가·저가 도달 순서), 부분 체결, 주문 잔여량, 5주 보유 시 3주 익절 후 잔여 수량 같은 주문 코어 제약
- 종목군의 실제 PIT 여부, 라이선스·이용권 진위
- 누락 봉 탐지, 세션 간 기준가격 혼합 방지 (계획 엔진 범위)

따라서 독립 검증(Antigravity)에서 위 항목은 "검사기 범위 밖"으로 구분해 봐야 하며, 이 보고서의 테스트 통과를 가격 체결·주문 코어의 통과로 해석하면 안 된다. 기존 전략 기각 결정은 바꾸지 않았고 새 전략도 채택하지 않았다.

## 10. 작업 방식상 이탈 사항

- 작업 지시는 파일 생성에 `apply_patch`를 쓰라고 했으나 이 세션에는 해당 도구가 없어 Write 도구로 허용 경로 안에서만 생성했다.
- 공백 검사를 위해 `git add -N`을 한 번 실행한 뒤 `git reset`으로 되돌렸다. 최종 index는 작업 전과 같다(작업 결과물은 모두 untracked).
- 작업 중 `docs/agent-tasks/session-engine-preflight-review-v1.md`가 이 작업 공간에 나타났다. 이 작업의 산출물이 아니며 열람·수정하지 않았다.
- 커밋·푸시는 하지 않았다.

## 11. Post-review correction: 요구 세션 행 존재 여부의 종목별 검사

**재현된 결함**: Codex가 pass 예시의 `manifest.sessionCoverage`를 `["REGULAR", "AFTER"]`로 바꾸고 첫 행을 복제해 `instrumentId=SYN-NO-REGULAR`, `session=AFTER`로 추가했을 때, 다른 종목에 REGULAR 행이 있다는 이유로 데이터셋 전체 검사(`REQUIRED_SESSION_NO_ROWS`)가 통과되어 요구 세션 행이 없는 종목까지 PASS로 판정됐다.

**수정**: 기존 전역 검사는 그대로 두고, 관측된 각 종목(`instrumentId`를 확인할 수 있는 종목)마다 요구 세션 행이 있는지 추가로 확인한다. 없는 종목은 `INSTRUMENT_REQUIRED_SESSION_NO_ROWS`(BLOCKED, `field`는 그 종목의 첫 행 `rows[i].session`)를 받는다.

| 사례 | 전체 `status` | `instrumentStatus` |
|---|---|---|
| mixed (SYN-A는 REGULAR 있음, SYN-NO-REGULAR는 AFTER만) | BLOCKED | SYN-A PASS, SYN-NO-REGULAR BLOCKED (전역 이슈 없음) |
| all-missing (전 종목이 AFTER만) | BLOCKED | 전 종목 BLOCKED (전역 + 종목별 이슈) |
| all-covered (전 종목이 REGULAR 보유, 추가 세션 허용) | PASS | 전 종목 PASS |

- `instrumentId` 자체가 없는 행(`<unattributed>`)에는 종목별 이슈를 만들지 않는다. 그 행은 이미 `MISSING_FIELD`로 차단된다.
- 이 보정은 요구 세션 행의 존재 여부만 다룬다. 해당 행이 실제 그 세션의 거래였는지, 세션 시간대가 맞는지는 검사하지 않는다.
- **이 검사기와 보정은 실제 데이터 검증도 수익 검증도 아니다.** 합성 데이터의 구조·내부 일관성 검사이며 PASS는 시장 사실성·라이선스·수익성을 인증하지 않는다.
- 작업 방식: `apply_patch`가 이 세션에 없어 Edit/Write 도구로 허용 경로 안에서만 수정했다. 이번 보정에서는 git index를 변경하지 않았고 커밋·푸시도 하지 않았다.
