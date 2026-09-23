# Agent Task Contract: Toss 캔들 페이지 커서의 이력 끝 동작 확인

상태: **초안 (사용자 수락 전)**. 2026-09-23 Claude 리뷰에서 도출했다. 대상 커밋은 `41ec765`(Toss 일봉 중복 제거)와 `6ff3523`(페이지 수신 시각 보존)이다.

## Identity

- Task ID: `toss-candle-cursor-20260923`
- Owner: 1단계 관측은 Claude가 관측 도구(`build/toss-cursor-probe/probe.py`, git 제외 경로) 작성과 결과 정리를 맡고, 자격 증명 입력과 실행은 사용자가 맡는다. 2단계 코드 조정은 Claude 또는 Codex이며, 새 허용 범위를 설정한 뒤에만 진행한다. Claude가 구현하면 교차 검증과 최종 승인은 Codex 또는 사용자가 한다.
- Work mode: 1단계 `review`(읽기 전용 관측), 2단계 `scoped-implementation`
- Branch / worktree: `main` 기준 작업 트리

## 배경

`TossSecuritiesMarketHistoryProvider.loadDailyCandles`는 `41ec765`부터 다음 두 경우에 조회를 중단하지 않고 `MarketDataUnavailableException`을 던진다.

- `nextBefore`가 이전에 받은 커서와 같은 경우. 이전에는 직전 커서와 같으면 `break`했다.
- 페이지에 새 거래일이 하나도 없는데 `nextBefore`가 남아 있는 경우. 이전에는 결과를 그대로 누적했다.

주봉 1회 조회는 `max(200, 101*7+30) = 737`개의 일봉을 요청한다. 페이지당 최대 200개이므로 4페이지다. 상장한 지 약 737거래일(약 2.9년)이 안 된 종목은 이력의 끝에 닿는다. **이력의 끝에서 Toss가 무엇을 반환하는지는 실제 응답으로 확인한 적이 없다.** 현재 단위 테스트(`repeatedPageCursorFailsWithoutUnboundedRequests`, `duplicateOnlyPageFailsWithoutUnboundedRequests`)는 가정한 응답을 모의한 것이다. 2026-09-23 기준 `TossSecuritiesMarketHistoryProviderTest` 6개는 모두 통과한다.

이력의 끝에서 같은 커서나 겹치는 페이지가 오면, 정상 종목인데도 장전 가이드와 전략 신호에서 **조회 불가**가 된다.

## Outcome

실제 Toss `/api/v1/candles`(`interval=1d`, `adjusted=true`)가 이력의 끝에서 보이는 페이지 동작을 관측한다. 그 결과로 현재 실패 처리를 유지할지, 정상 종료로 분류할지 결정 근거를 남긴다.

## 1단계: 관측 (읽기 전용)

### 대상 종목

- 이력이 짧은 US 종목 1~2개. 상장 1년 안팎의 ETF나 종목으로, 실행하는 사람이 선정하고 상장일을 기록한다.
- 대조군으로 이력이 긴 종목 1개(예: 기존 테스트의 `SOXL`)를 쓴다.
- 가능하면 KR 종목 1개도 포함한다. 토스 연결 계좌가 지원하는 경우에만 한다.

### 페이지마다 기록할 항목

| 항목 | 설명 |
|---|---|
| 요청 `count`, `before` 유무 | 커서 값은 그대로 기록하지 않고 "이전 커서와 같음/다름/null/blank"로만 기록한다 |
| 반환 캔들 수 | |
| 최소·최대 거래일 | |
| 이전 페이지와 겹치는 날짜 수 | |
| `nextBefore` 상태 | null / blank / 새 값 / 이전 커서와 같음 |
| HTTP 상태 | 이력의 끝에서 4xx가 오는지도 확인한다 |

마지막 페이지 이후 한 번 더 요청했을 때의 응답도 기록한다.

### 산출물

`docs/design/GUIDE_INPUT_OBSERVATION.md`에 붙일 관측 요약을 작성한다. 관측일, 종목, 상장일, 페이지별 표, 결론을 포함한다.

## 2단계: 판정과 코드 조정 (관측 결과에 따라)

| 관측 결과 | 조치 |
|---|---|
| 이력의 끝에서 빈 페이지 또는 `nextBefore` null/blank | 코드 변경 없음. 관측 근거를 문서에 기록하고, 실제 형태를 재현하는 회귀 테스트를 1개 추가한다 |
| 같은 커서 반복, 또는 새 날짜 없는 페이지 | 이 경우만 "이력 끝"으로 정상 종료하도록 조정한다. 단 **같은 날짜의 값 충돌은 계속 실패**로 처리한다. 수집된 일봉이 부족하면 기존 주봉 신선도·이력 검증에 맡긴다 |
| 이력의 끝에서 4xx | 해당 상태 코드를 정상 종료로 볼지 별도로 판단하고, 다른 4xx(인증·권한)와 구분한다 |
| 종목마다 다름 | 임의 규칙을 만들지 않고, 관측 표를 첨부해 사용자에게 보고한다 |

정상 종료로 바꾸더라도 무한 요청을 막는 장치(반복 커서 감지와 페이지 상한)는 유지한다.

## Allowed Files

2단계에서 코드 조정이 필요할 때만 적용한다. 이 작업 계약 파일 작성 시점의 로컬 범위는 이 파일 하나다.

- `src/main/java/com/tradeguide/service/broker/TossSecuritiesMarketHistoryProvider.java`
- `src/test/java/com/tradeguide/service/broker/TossSecuritiesMarketHistoryProviderTest.java`
- `docs/design/GUIDE_INPUT_OBSERVATION.md` (관측 요약 추가)

## Non-Goals And Guardrails

- 관측 없이 동작을 바꾸지 않는다. 1단계 결과가 없으면 2단계는 시작하지 않는다.
- `pageReceivedAt`은 실제로 받은 페이지마다 한 건씩 남긴다. 정상 종료로 바꾼 경우에도 마지막(빈 또는 중복) 페이지의 수신 기록을 어떻게 다룰지 명시하고 테스트한다.
- 동일 날짜 OHLCV 충돌은 어떤 경우에도 실패로 처리한다.
- 전략 규칙과 수익성은 바꾸지 않는다. 조회 불가 오분류만 다룬다.
- API 계약, DB 스키마, 인증, 캐시·가이드 서비스, `frontend/**`, `research/**`, 정책·하네스, 비밀 설정은 이 작업에서 읽기 전용이다. 사용자 소유의 `research/scripts/track-a-infinite-buy/results/`는 건드리지 않는다.
- 액세스 토큰, 앱 키·시크릿, 계좌번호, 원본 커서 문자열, 응답 본문 전체를 문서·로그·대화에 기록하지 않는다.
- 1단계는 캔들 조회만 한다. 주문·계좌·보유내역 API는 호출하지 않는다. 관측용 스크립트는 저장소 밖 로컬 임시 경로에 두고 커밋하지 않으며, 자격 증명은 환경변수로만 주입한다.
- Claude는 커밋·푸시하지 않는다(사용자가 해당 턴에 특정 커밋을 명시적으로 요청한 경우는 정책에 따른다).

## Acceptance Checks

- [ ] 1단계: 종목·상장일·관측일·페이지별 표를 담은 관측 요약과, 판정 표의 어느 행에 해당하는지
- [ ] 2단계(코드 변경 시): `./gradlew test --tests '*TossSecuritiesMarketHistoryProviderTest'` 통과
- [ ] 2단계(코드 변경 시): 전체 백엔드 테스트 `./gradlew test` 통과
- [ ] 2단계: `git diff --check` 통과
- [ ] 보고는 한국어로 하고, 통과한 검사와 실행하지 않은 검사를 구분한다

## Handoff

- 1단계 결과(2026-09-23): 판정 표 첫 행. 이력 끝에서 Toss는 요청보다 적은 캔들과 `nextBefore=null`을 반환했다(IBIT, CRCL). 커서 반복·새 날짜 없는 페이지는 관측되지 않았다. 상세는 `docs/design/GUIDE_INPUT_OBSERVATION.md`의 "관측: Toss 캔들 커서의 이력 끝 동작".
- Files changed: `docs/design/GUIDE_INPUT_OBSERVATION.md`(관측 요약). 관측 도구는 `build/`(git 제외)에만 있다.
- Verification run: 관측 도구를 모의 서버로 두 시나리오(null 종료, 커서 반복) 확인한 뒤 사용자가 실제 Toss API로 실행했다.
- API / data-model / policy impact: 없음. 프로덕션 코드 변경 없음.
- 2단계 결과(2026-09-23, Claude 구현): 판정 표 첫 행에 따라 프로덕션 코드는 바꾸지 않고, 실제 응답 형태(꽉 찬 200개 페이지 뒤 요청 100개에 40개 + `nextBefore=null`)를 재현하는 `historyEndReturnsShortPageWithNullCursorAndStopsWithoutExtraRequest`를 `TossSecuritiesMarketHistoryProviderTest`에 추가했다. 두 번째 요청의 `count`·`before`, 240개 오름차순·중복 없음, 페이지 수신 기록 2건, 추가 요청 없음을 검증한다.
- 2단계 검증: `./gradlew test --tests '*TossSecuritiesMarketHistoryProviderTest'` 7개 통과, 전체 `./gradlew test` 1,111개 통과(실패 0, 건너뜀 3). 교차 검증·최종 승인은 사용자(정책상 Claude 구현분).
- 국내 종목 관측(2026-09-23 12:18 KST): 005930(대조군), 462870, 278470 모두 US와 같은 형태(요청보다 적은 캔들 + `nextBefore=null`)로 정상 종료했다. 상세는 `docs/design/GUIDE_INPUT_OBSERVATION.md`의 "국내 종목".
- Open decision or risk: 없음. 한 시점의 관측이므로 제공자 동작이 바뀌면 추가된 회귀 테스트와 관측 도구로 다시 확인한다.
