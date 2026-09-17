# 자산별 데이터 계층(Tier) 매핑표

- 날짜: 2026-09-17
- 배경: `research/STRATEGY_ENGINE_POLICY.md` "자산 분류 프레임 재정의(2026-09-17)" 절의
  다음 단계 2번 — 현재 관리 중인 자산이 Tier 1/2/3 중 어디에 속하는지 실제 데이터
  제공자 기준으로 확인.
- 방법: 기존 리서치 리포트(Track A/B/C, 국내 레버리지 ETF 조사)에서 이미 실제로 검증한
  사실을 모으고, 국내 종목의 Finnhub 펀더멘털 접근 가능 여부는 이번에 다시 실제 API
  호출로 재확인했다(아래 "재확인" 절).

## Tier 정의 (재인용)

- **Tier 1(가격만)**: 가격·거래량만 확보 가능.
- **Tier 2(가격+펀더멘털)**: 가격에 더해 재무 시계열(PE/PB/ROE/마진/부채비율 등)까지 확보 가능.
- **Tier 3(가격+펀더멘털+매크로 연계 확인)**: 매크로 지표와의 연계 분석까지 실제로 시도할 수 있는 자산군. 매크로 데이터 제공자 조사(목표 B)가 선행돼야 하며, 아직 어떤 자산도 이 계층에 없다.

## 매핑표

| 자산군 | 대표 종목/유니버스 | 가격 데이터 | 펀더멘털 데이터 | 매크로 연계 | Tier | 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| Track A-US (레버리지, 미국) | SOXL, TQQQ | O — Twelve Data(운영), Yahoo Finance(리서치) | 해당 없음 — 지수 추종 레버리지 ETF라 EPS/PE 같은 회사 실적 개념 자체가 없음 | 미확인 | **Tier 1** | 기존 SOXL/TQQQ 백테스트(`research/data/backtests.json` soxl-* 항목들)가 전부 가격 데이터만 사용 |
| Track A-KR (레버리지, 국내) | KODEX 레버리지(122630), KODEX 코스닥150레버리지(233740), TIGER 200선물레버리지(267770) | O — Yahoo Finance(리서치), 운영 경로는 Toss증권 일봉 집계 예정 | 해당 없음 — Track A-US와 동일 이유(지수 추종 ETF) | 미확인 | **Tier 1** | `research/reports/track-a-kr-leverage-etf-data-feasibility.md` |
| Track B-US (일반주, 미국) | S&P100 워크포워드 유니버스(AAPL/JPM/PG 등) | O — Twelve Data(운영), Yahoo Finance(리서치) | O — Finnhub 무료 티어(`series.annual`/`series.quarterly`, PE/PB/ROE/마진/부채비율). 은행/금융지주 7종(BAC/BNY/C/COF/JPM/USB/WFC)은 EPS 시계열 결측으로 PEG류 팩터 계산에서 제외 | 미확인 | **Tier 2** | `research/reports/track-b-fundamental-data-provider-evaluation.md`, `research/reports/track-b-peg-valuation-validation.md` |
| Track B-KR ((구)Track C, 일반주, 국내) | KOSPI200 199종목 | O — Yahoo Finance(리서치), 운영 경로는 Toss증권/한국 시장 확대 예정 | **X** — Finnhub 무료 티어가 한국 종목에 403 Forbidden 반환 | 미확인 | **Tier 1** | 2026-09-17 재확인(아래 절). 기존 `research/TASKS.md` 8절의 관찰과 일치 |

## 재확인 — Finnhub 무료 티어의 한국 종목 펀더멘털 접근

기존 리서치가 "한국 종목 펀더멘털 접근 불가(403)"라고 기록해 둔 것을, 오늘 실제 API
호출로 다시 확인했다(`research/scripts/track-b-walk-forward/fetch_finnhub_fundamentals.py`의
`fetch_one` 함수를 그대로 재사용, 삼성전자 005930 대상).

| 요청 심볼 형식 | 결과 |
| --- | --- |
| `005930.KS` (Yahoo 스타일) | `HTTPError 403: Forbidden` |
| `005930.KX` | 200 OK, 그러나 `metric`/`series` 모두 비어 있음(데이터 없음) |
| `KRX:005930` | 200 OK, 그러나 `metric`/`series` 모두 비어 있음(데이터 없음) |

심볼 표기를 바꿔도 실제 재무 데이터는 얻을 수 없다 — 403이든 빈 응답이든 결과적으로
한국 종목에 Finnhub 무료 티어 펀더멘털을 쓸 수 없다는 결론은 동일하다. Track B-KR이
Tier 1으로 남는 이유는 국내 시장 자체의 한계가 아니라 **현재 확보한 무료 데이터
제공자의 한계**이며, 유료 제공자(Finnhub Global $3,500/월 등)를 도입하면 바뀔 수 있는
가정이라는 점을 분명히 해 둔다.

## 관찰

- **4칸 중 3칸이 Tier 1**이다 — 레버리지 자산(미국·국내 공통)과 국내 일반주 모두 가격
  데이터만으로 방법론을 구성해야 한다. 펀더멘털 기반 방법론(Track B-US 스타일)을 쓸 수
  있는 건 현재 미국 일반주뿐이다.
- Tier 2(Track B-US)에서도 실제로는 9개 후보 전부 기각됐다(`research/TASKS.md` 7절) —
  즉 "데이터가 더 많다"는 것이 "더 나은 전략을 찾는다"는 것을 보장하지 않는다는 것이
  이미 실증됐다.
- Tier 3(매크로 연계)은 어떤 자산도 아직 없다. 목표 (B) 매크로 가이드 엔진을 시작하려면
  매크로 데이터 제공자 조사가 반드시 선행돼야 한다.

## 다음 단계

`research/TASKS.md`의 (A) 자산군 일반화 항목은 이 매핑으로 사실상 마무리됐다. 남은
항목은 "(구)Track C(국내 일반주) 재시도 범위 확정"이며, 이 매핑이 그 답을 이미 준다 —
국내 일반주는 Tier 1이므로 Track B의 펀더멘털 기반 후보(PEG/퀄리티)는 애초에 이식
불가하고, 가격 기반 후보(모멘텀 등)만 시도 가능하다는 제약을 확정한다. (A)가 끝나면
목표 (B) 매크로 데이터 제공자 조사로 넘어간다.
