# Track B(일반 종목) 전략 후보 카탈로그와 채택 권고

## 핵심 결론 (3~5줄)

Track A의 10주/40주 이동평균 크로스오버 규칙을 Track B(일반 대형주)에 그대로 재사용하는 안은 이미 내부 백테스트(`trackb-ma-timing-regular-stocks.md`)로 **기각**됐다 — AAPL/JPM/PG 3종 모두 단순 보유 대비 열위였다. 이번 조사에서 학술·공식 문헌 기준으로 성격이 다른 후보 4개(①Track A 규칙 재사용 ②PEG/PER 밸류에이션 밴드 ③횡단면 모멘텀 ④저변동성·퀄리티 팩터)를 비교한 결과, **지금 시점에 Track B 엔진(실제 `TradingStrategy` 구현과 `BUY`/`SELL` 행동 규칙)을 구현할 만큼 검증이 끝난 후보는 없다.** 가장 유망한 다음 단계는 **횡단면 모멘텀(12-1개월류 상대강도)** 이지만, 이는 "채택"이 아니라 "이 회사의 실데이터·비용 가정으로 워크포워드 백테스트를 먼저 수행해야 하는 다음 조사 과제"로 제안한다. PEG/PER 밸류에이션과 퀄리티/저변동성 팩터는 둘 다 이 코드베이스에 아직 없는 펀더멘털 데이터 제공자가 필요해 현재는 `CANDIDATE`/`WATCH` 수준의 조사 후보로만 유지해야 한다. 별도 Track C는 지금 만들 근거가 없다.

## 조사 방법

- Track A 구현(`WeeklyMaCrossoverStrategy`, `StrategySelector`, `StrategyDecisionMaker`, `InvestmentTrack`, `MarketDataProvider`)과 `docs/PROJECT_CONTEXT.md`, `research/STRATEGY_ENGINE_POLICY.md`, 기존 Track B 리서치(`trackb-ma-timing-regular-stocks.md`, `two-track-strategy-framework.md`, `relative-valuation-band.md`, `analyst-consensus-target-price.md`)를 먼저 읽고 현재 계약과 이미 확보된 증거를 확인했다.
- 코드 확인 결과 `InvestmentTrack.TRACK_B`는 enum에 존재하지만 이를 `supports()`하는 `TradingStrategy` 구현체가 없어 `StrategySelector.select(TRACK_B)`는 `UnsupportedInvestmentTrackException`을 던진다 — Track B 실행 엔진은 **아직 전혀 구현되지 않았다.**
- `MarketDataProvider` enum(`TWELVE_DATA`, `TOSS_SECURITIES`, `YAHOO_FINANCE`)은 모두 가격·캔들 제공자이며, PEG/PER 등 펀더멘털 데이터를 조회하는 제공자는 하나도 없다. `research/data/candidates.json`의 PEG/PER 값은 Finviz 스크리너에서 수동으로 가져온 스냅샷(`raw_query_url` 필드)이며, 앱 자체의 데이터 파이프라인을 거치지 않는다 — 즉 밸류에이션 기반 후보는 재현 가능한 자동 데이터 소스가 아직 없다는 뜻이다.
- 학술·공식 1차 자료는 웹 검색으로 확인했고, 각 후보 절과 [출처](#출처)에 논문/공식 방법론 문서 링크를 남겼다.
- 이 리포트는 연구 산출물이며, `research/**` 범위 밖(코드·정책 문서)은 수정하지 않았다. 여기서의 결론은 정책 채택이 아니라 제안이다(`research/STRATEGY_ENGINE_POLICY.md`의 "학습/조사 후보" 규칙).

## 후보 비교

| 후보 | 가설 | Track A와의 차별성 | 필요 데이터 | 현재 이 코드베이스에서의 실행 가능성 | 판정 |
|---|---|---|---|---|---|
| ① Track A 규칙 재사용(10주/40주 MA 크로스오버) | 단일 종목 추세추종 | 없음 — 동일 규칙 | 가격(이미 있음) | 가능하지만 이미 기각된 규칙 | **기각** |
| ② PEG/PER 밸류에이션 밴드 | 펀더멘털 저평가·평균회귀 | 가격이 아닌 이익 배수 기반 | PEG/PER/실적일(**미보유**) | 불가 — 펀더멘털 제공자 없음 | **보류(데이터 인프라 선결)** |
| ③ 횡단면 모멘텀(상대강도) | 동일 유니버스 내 상대적 승자 지속 | 단일 종목 추세가 아니라 유니버스 내 순위 | 가격(주봉, 이미 있는 종류지만 유니버스 규모 확장 필요) | 데이터는 가능, 백테스트 미수행 | **다음 조사 우선순위(미채택)** |
| ④ 저변동성/퀄리티 팩터 | 안전자산 이상현상 | 변동성·재무건전성 기준 스크리닝 | 가격(저변동성 부분) + 재무제표(퀄리티 부분, **미보유**) | 저변동성만 부분 가능, 퀄리티는 불가 | **보류(퀄리티 절반은 데이터 인프라 선결)** |

---

### ① Track A 규칙 재사용 — 기각

- **규칙**: 완료 주봉 10주/40주 이동평균 크로스오버를 Track B 종목에 그대로 적용.
- **근거와 결과**: `research/reports/trackb-ma-timing-regular-stocks.md`에서 AAPL/JPM/PG 3종에 이미 실데이터로 검증했다. 세 종목 모두 전략 누적수익률이 단순 보유보다 낮았다(AAPL +19.8% vs +100.7%, JPM +160.7% vs +221.2%, PG +3.6% vs +6.0%). `research/reports/two-track-strategy-framework.md`는 이 차이를 "Track A(SOXL)에서의 우위는 레버리지 ETF 특유의 -89.8% 대형 낙폭을 무포지션으로 피한 단일 사건이 거의 전부를 설명하며, 대형 낙폭 자체가 없는 안정적 우량주에는 규칙의 단점(신호 지연·횡보 휩쏘)만 남는다"고 설명한다.
- **판정**: 과제 계약상 "Track A와 재질적으로 다른 가설이어야 한다"는 요건도 애초에 충족하지 못하고, 이미 확보된 실데이터도 부정적이다. **채택하지 않는다.**

### ② PEG/PER 밸류에이션 밴드 — 데이터 인프라 선결 과제로 보류

- **가설**: 이익 대비 저평가된 종목이 평균회귀로 초과수익을 낸다(가치 프리미엄).
- **1차 학술 근거**: Lakonishok, Shleifer & Vishny(1994), *Contrarian Investment, Extrapolation, and Risk*, *The Journal of Finance* 49(5), 1541–1578 — 가치주 전략의 초과수익은 위험 프리미엄이 아니라 투자자들이 과거 실적을 과도하게 미래로 외삽하는 행동 편향에서 기인한다고 주장한 대표 논문.
- **현재 프로젝트 내 상태**: `research/STRATEGY_ENGINE_POLICY.md`의 Track B 절은 이미 "PEG > 0 && PEG < 1 && 현재가 > 50일 SMA"를 후보 발굴 조건으로만(자동 매수 신호 아님) 채택했고, `research/data/candidates.json`에 2026-08-05 스냅샷이 있다. 하지만 이 값은 앱의 실행 데이터 파이프라인이 아니라 Finviz 스크리너 수동 조회 결과다.
- **왜 지금 엔진에 넣을 수 없는가**:
  1. **데이터 제공자 부재**: `MarketDataProvider`에는 PEG/PER/실적 발표일을 제공하는 항목이 없다. 자동화하려면 새 펀더멘털 데이터 제공자 계약이 필요하고, 이는 `docs/AI_COLLABORATION_POLICY.md` 기준 사용자 승인이 필요한 "외부 제공자 변경"이다.
  2. **Point-in-time 편향**: PEG/PER는 특정 시점의 (수정 이전) EPS 추정치가 필요하다. 사후에 수정된 재무제표로 계산하면 미래 정보 누출(look-ahead bias)이 생긴다. 무료 소스로는 이 시점별 원본 데이터를 안정적으로 구하기 어렵다는 한계가 이미 `two-track-strategy-framework.md`에도 기록돼 있다.
  3. **행동 규칙 미정**: 정책 문서 자체가 "후보 결과의 기본 행동은 `CANDIDATE` 또는 `WATCH`다. 매수 가이드가 되려면 가격 기준, 무효화 가격, 데이터 기준일과 위험 요인이 추가돼야 한다"고 명시한다 — 즉 밸류에이션 후보는 매수/매도 타이밍 규칙 자체가 아직 없다.
- **판정**: 가설과 문헌 근거는 견고하지만, 이 회사 코드베이스에서 지금 바로 실행 가능한 후보가 아니다. 스크리닝(발굴) 용도로는 유지하되, 타이밍/행동 규칙으로 승격하려면 펀더멘털 데이터 제공자 결정이 선행돼야 한다.

### ③ 횡단면 모멘텀(상대강도, cross-sectional momentum) — 다음 조사 우선순위(미채택)

- **가설**: 최근 일정 기간 동안 유니버스 내에서 상대적으로 우수한 성과를 낸 종목이 향후에도 상대적 우위를 이어간다(단일 종목의 절대적 추세가 아니라, 같은 유니버스 안에서의 상대적 순위).
- **1차 학술 근거**:
  - Jegadeesh & Titman(1993), *Returns to Buying Winners and Selling Losers: Implications for Stock Market Efficiency*, *The Journal of Finance* 48(1), 65–91 — 3~12개월 보유 기간에서 과거 승자를 매수하고 패자를 매도하는 전략이 월 약 1%대의 초과수익을 냈다는 원조 논문.
  - Fama & French 모멘텀 팩터(Kenneth R. French Data Library, "Detail for Monthly Momentum Factor (Mom)") — 형성 기간을 직전 12개월 수익률에서 **가장 최근 1개월을 제외**(이른바 "12-2" 또는 "12-1" 구성)해 단기 되돌림(short-term reversal) 효과를 걸러내는 표준 관행을 공식적으로 정의한다.
  - Asness, Moskowitz & Pedersen(2013), *Value and Momentum Everywhere*, *The Journal of Finance* 68(3), 929–985 — 모멘텀이 미국 주식뿐 아니라 8개 시장·자산군에서 공통적으로 나타나며, 가치 요인과는 음의 상관을 갖는다는 것을 보임.
  - S&P Dow Jones Indices, *S&P Momentum Indices Methodology* (공식 방법론 문서) — 상대적 성과의 지속성을 측정하는 규칙 기반·반기 리밸런싱 지수로 실무에서 어떻게 구조화하는지 보여주는 공식 자료.
- **Track A와의 차별성**: Track A는 "이 종목 하나가 자기 자신의 과거 대비 상승/하락 추세인가"를 본다(시계열/절대적 추세추종). 모멘텀은 "이 종목이 같은 유니버스의 다른 종목들과 비교해 상대적으로 강한가"를 본다(횡단면/상대강도). 매매 로직, 필요 데이터 범위(단일 종목 vs 전체 유니버스), 실패 모드가 모두 다르다.
- **제안 규칙(검증 대상, 아직 미채택)**:
  - 유니버스: 현재 정책과 동일하게 S&P 500(또는 그 중 `TRACK_B` 프로필로 등록된 부분집합)으로 제한.
  - 형성 기간: 학술 관행(12-1개월)을 이 서비스가 이미 쓰는 완료 주봉 데이터에 맞게 번역해 **직전 52주 수익률 중 최근 4주를 제외**한 48주 수익률로 정의(단기 되돌림 배제 취지 유지).
  - 순위/행동: 유니버스 내 형성기간 수익률 상위 구간(예: 상위 20%)을 `CANDIDATE`, 하위 구간을 `WATCH`로 표시. Track A와 동일하게 `StrategySignal`(시장 사실)과 `StrategyDecision`(보유 맥락 반영 행동)을 분리하고, `BUY`/`SELL` 확정 전에는 정책 채택과 사용자 승인을 거친다.
  - 리밸런싱: 매주가 아니라 **분기 단위**로 제한하고, 이미 편입된 종목은 순위가 더 낮은 하한선(예: 상위 40% 밖으로 이탈)까지는 유지하는 진입/유지 이원 기준("buy/hold spread")을 적용해 회전율을 낮춘다 — 아래 "가정" 절의 회전율 근거 참고.

#### 필요 데이터

- 확정 주봉 종가, 최소 52주 이력 — 현재 `TWELVE_DATA` 주봉 조회로 이미 가능한 종류의 데이터이지만, **한 종목이 아니라 유니버스 전체(최대 S&P500 규모)를 정기적으로 스캔**해야 한다는 점이 Track A와 다르다. `PortfolioCandidateStrategyGuideService`에 이미 있는 429 요청 제한 처리(요청 제한 시 남은 종목을 `unavailableAssets`로 기록)를 그대로 재사용할 수 있지만, 유니버스 규모의 정기 스캔이 현재 Twelve Data 요금제의 요청 한도로 감당 가능한지는 별도로 확인해야 한다.
- 펀더멘털 데이터는 필요 없다 — 이는 밸류에이션/퀄리티 후보 대비 이 후보의 실질적 장점이다.
- 유니버스 구성 목록(S&P500 편입 종목 리스트)의 **시점별(point-in-time)** 정확성. 현재 편입 종목 리스트만 쓰면 생존 편향(survivorship bias)이 생긴다 — 과거 지수에서 퇴출된 종목이 표본에서 빠지므로 성과가 과대평가될 수 있다.

#### 가정(거래비용·생존편향)

- **거래비용/슬리피지**: Novy-Marx & Velikov(2016), *A Taxonomy of Anomalies and Their Trading Costs*, *The Review of Financial Studies* 29(1), 104–147은 TAQ 데이터 기준 중간 회전율 이상현상의 평균 실행비용이 왕복 20~57bp라고 실측했고, 월 회전율 50%를 넘는 전략은 비용을 넘는 순수익을 내기 어렵다고 보고했다. 이 프로젝트가 Track A 손절 재검증에서 이미 쓴 슬리피지 가정(`0.15%p`, `track-a-stoploss-revalidation-and-sizing-design.md`)과 정합적으로, 모멘텀 백테스트에도 최소 왕복 20~57bp 구간의 거래비용을 명시적으로 반영해야 한다. 분기 리밸런싱 + buy/hold 이원 기준을 제안 규칙에 넣은 이유도 이 회전율 제약 때문이다.
- **생존편향**: 현재 S&P500 편입 종목만 쓰면 과거 퇴출 종목이 빠져 결과가 낙관적으로 왜곡된다. 이 프로젝트가 시점별 지수 편입 데이터(유료 CRSP/Compustat 수준)를 확보하기 전까지는, 이 편향을 리포트에 **명시적으로 caveats로 기록**하고 결론을 과신하지 않는 조건으로만 결과를 사용한다.

#### 지표

- 유니버스 대비 절대 수익률이 아니라, Track A 검증 관행과 동일하게 **동일가중 매수 후 보유(buy-and-hold) 벤치마크 대비 초과수익**, 샤프비율, 최대낙폭(MDD), 형성기간별 승률, 월/분기 회전율, 비용 차감 후 순스프레드를 함께 기록한다.

#### 워크포워드/샘플 외 검증 방법

- 이 프로젝트가 Track A 손절 재검증(`track-a-stoploss-revalidation-and-sizing-design.md`)에서 확립한 관행을 그대로 따른다 — **단일 통합 표본으로 결론을 내리지 않는다.** 그 리포트는 워크포워드 분할 기준(2019 vs 2020)을 바꾸는 것만으로 "-25% 손절이 거의 공짜"라는 결론의 부호 자체가 뒤집힌 사례였다.
- 최소 두 개의 사전 고정된 비중첩 분할을 사용한다(예: 학습 2015–2019 / 검증 2020–2026, 그리고 뒤집은 분할 학습 2020–2022 / 검증 2023–2026). 두 분할 모두에서 방향이 같아야 신뢰할 수 있다.
- 가능하면 Track A 추적 손절 검토(`track-a-trailing-stop-review.md`)처럼 4분할(예: 2018/2019/2020/2021 기준)로 확장해 특정 구간 의존성을 추가로 점검한다.

#### 채택 게이트(엔진 구현 착수 전 반드시 통과해야 하는 조건)

Track A가 세 가지 손절 후보를 모두 "추가 검증 필요/채택 비추천"으로 기각한 것과 같은 엄격도를 적용한다. 아래 중 하나라도 미충족이면 채택하지 않는다.

1. 위에서 정의한 왕복 20~57bp 거래비용과 슬리피지를 반영한 뒤에도, **두 개 이상의 독립적 워크포워드 분할 모두에서** 동일가중 매수 후 보유 벤치마크 대비 순수익이 양(+)이어야 한다. 한 분할에서만 이기면 기각.
2. 분할별 결론의 **방향(부호)이 뒤집히지 않아야** 한다 — Track A 손절 재검증에서 발견된 불안정성 패턴이 재현되면 "추가 검증 필요"로 보류한다.
3. 월 환산 회전율이 Novy-Marx & Velikov가 제시한 순생존 가능 구간(대략 월 50% 미만)을 넘지 않아야 한다. 넘으면 buy/hold 이원 기준을 더 넓히거나 리밸런싱 주기를 늘려 재시도한다.
4. 최대낙폭이 벤치마크보다 뚜렷이 나쁘지 않아야 한다(Daniel & Moskowitz(2016)의 모멘텀 붕괴 위험이 실제로 이 표본에 나타나는지 확인 — 아래 "리스크" 참고).
5. 결과는 `research/data/backtests.json` 스키마에 원자료로 기록하고, 이 리포트가 아니라 **실제 계산이 끝난 후속 리포트**가 confidence와 caveats를 부여한다. 이 리포트 자체는 문헌 근거만 제시했으므로 confidence를 `low`로 유지한다(아래 confidence 라인 참고).

#### 리스크

- **모멘텀 붕괴(momentum crash)**: Daniel & Moskowitz(2016), *Momentum Crashes*, *Journal of Financial Economics* 122(2), 221–247 — 모멘텀 전략은 평상시 꾸준한 양의 수익을 내지만, 시장이 급락한 뒤 반등하는 "패닉" 국면에서 드물지만 급격하고 지속적인 손실을 낼 수 있다("과거 패자"가 옵션처럼 반등하기 때문). 이 위험은 채택 게이트의 MDD 조건으로 일부 걸러지지만, 완전히 제거되지는 않는다는 점을 전략 설명에 명시해야 한다.
- **회전율·비용 침식**: 위 가정 절 참고. 분기 리밸런싱과 buy/hold 이원 기준으로 완화하되, 실제 구현 시 재확인 필요.
- **데이터 엔지니어링 비용**: 유니버스 규모(최대 500종목) 정기 스캔은 현재 종목 단건 조회 패턴과 다른 배치/캐시 설계가 필요하다. Twelve Data 요청 제한(429) 처리 로직은 이미 있지만, 요금제 자체의 처리량이 충분한지 확인이 선행돼야 한다.
- **생존편향**: 위 가정 절 참고 — 과대평가 방향의 편향이므로 채택 게이트를 더 보수적으로 해석해야 한다.
- **행동 매핑 미정**: 상대강도 순위를 `StrategyAction`(`BUY`/`WATCH` 등)으로 매핑하는 구체적 임계값은 이 리포트가 정의하지 않는다. Track A의 `weeksSinceCross <= 4`처럼 근거 있는 임계값을 도출하려면 채택 게이트를 통과한 뒤 별도 조사가 필요하다.

### ④ 저변동성/퀄리티 팩터 — 데이터 인프라 선결 과제로 보류

- **가설(저변동성)**: 역사적으로 변동성이 낮은 주식이 위험 대비 수익률에서 고변동성 주식보다 열등하지 않거나 오히려 우월하다("저변동성 이상현상").
- **1차 학술 근거**:
  - Ang, Hodrick, Xing & Zhang(2006), *The Cross-Section of Volatility and Expected Returns*, *The Journal of Finance* 61(1), 259–299 — 개별 종목의 특이 변동성(idiosyncratic volatility)이 높을수록 향후 평균 수익률이 현저히 낮다는 것을 실증.
  - S&P Dow Jones Indices, *S&P 500 Low Volatility Index* 공식 방법론 — 직전 252거래일 일간 표준편차로 변동성을 계산해 하위 100종목을 변동성 역가중으로 담고 분기 리밸런싱하는 규칙 기반 방법론(공식 문서).
- **가설(퀄리티)**: 수익성·성장성·재무 안전성·배당성향이 높은 "우량" 기업이 "부실" 기업 대비 위험조정수익률이 높다.
  - 1차 학술 근거: Asness, Frazzini & Pedersen, *Quality Minus Junk*, *Review of Accounting Studies* — 퀄리티를 수익성(profitability)·성장성(growth)·안전성(safety)·배당성향(payout)의 네 축으로 정의하고, 퀄리티 롱-정크 숏 포트폴리오가 미국 및 24개국에서 유의한 위험조정수익을 낸다는 것을 보임.
- **Track A와의 차별성**: 가격 추세가 아니라 통계적 위험(저변동성) 또는 재무제표 기반 안전성/수익성(퀄리티)으로 종목을 고른다는 점에서 Track A(추세추종)나 위 모멘텀(상대강도) 어느 쪽과도 다른 세 번째 가설군이다.
- **왜 지금 채택할 수 없는가**: 저변동성 절반(가격의 표준편차)은 기존 가격 데이터만으로 계산 가능해 기술적으로는 실행 가능하지만, 퀄리티 절반(수익성·성장성·재무 안전성)은 ②와 동일하게 이 코드베이스에 없는 펀더멘털 데이터 제공자가 필요하다. 저변동성만 분리해 단독 후보로 쓰는 방안도 검토했으나, 학술적으로 저변동성 효과의 상당 부분이 퀄리티/수익성과 공통 요인을 공유한다는 것이 후속 연구들의 반복된 관찰이라 저변동성만 떼어 쓰면 근거가 약해진다. 이 후보는 밸류에이션(②)과 같은 이유로 지금은 보류한다.
- **판정**: 세 번째 가설군으로서 이론적 근거는 있으나, 이 프로젝트가 아직 만들지 않은 펀더멘털 데이터 인프라에 의존한다. 채택 여부를 지금 판단할 근거 자체가 부족하므로 **보류**.

## Track C 필요 여부

**지금은 별도 Track C를 만들 근거가 없다.**

- 전략 트랙(Track A/B/향후 C)은 "서로 다른 매매/타이밍 가설 + 그 가설에 필요한 데이터 + 검증된 행동 규칙"의 묶음이다. 반면 `PortfolioRiskPolicy`(주문당 최대 손실 비율, 종목당 최대 노출 비율)는 어떤 트랙에 속하든 포트폴리오 전체에 적용되는 **횡단적 위험 관리 계층**이다. 이미 구현·저장·조회 API까지 갖춘 `PortfolioRiskPolicy`는 새 트랙이 아니라, 모든 트랙의 결과 위에 얹히는 별도 축이다 — 이 구분은 `research/STRATEGY_ENGINE_POLICY.md`의 "Track A/B 구분"과 "포트폴리오 위험 관리"를 이미 별개 절로 다루고 있는 것과 일치한다.
- Track C 후보가 되려면 Track A(추세추종·단일 종목)나 Track B 후보들(밸류에이션/상대강도/퀄리티)과 재질적으로 다른 네 번째 가설이 필요하다. 이번 조사에서 검토한 후보 중 가장 근접한 것은 ④ 퀄리티 팩터이지만, 이는 ②와 같은 펀더멘털 데이터 인프라 부재로 막혀 있어 `research/STRATEGY_ENGINE_POLICY.md`의 의사결정 상태 분류 기준으로도 아직 "검증 중 가설"이 아니라 "학습/조사 후보" 단계에 머물러 있다.
- 따라서 Track C는 (a) Track B 모멘텀 후보의 채택 게이트 결과가 나오고, (b) 펀더멘털 데이터 제공자 도입 여부가 결정된 뒤에만 재검토할 문제이며, 지금 트랙을 하나 더 늘리는 것은 시기상조다.

## 단계별 구현 계획 (제안 — 아직 승인/구현되지 않음)

이 계획은 연구 산출물이며, 각 단계는 해당 작업 계약과 사용자 승인을 거쳐야 실행된다.

- **Phase 0 (현재 상태, 변경 없음)**: Track B는 `CANDIDATE`/`WATCH` 수준의 PEG/PER + 50일선 스크리닝만 유지한다. `StrategySelector`에 `TRACK_B` 구현체를 추가하지 않는다.
- **Phase 1 (research/** 범위, 다음 리서치 과제)**: 이 리포트가 제안한 횡단면 모멘텀 규칙을 실데이터로 워크포워드 백테스트한다. `research/data/backtests.json` 스키마에 원자료를 남기고, 위 "채택 게이트" 5개 조건 통과 여부를 실제 숫자로 판정하는 후속 리포트(`research/reports/track-b-momentum-backtest.md` 형태)를 작성한다. 생존편향 완화를 위해 가능하다면 시점별 유니버스 목록 확보 방안도 함께 조사한다.
- **Phase 2 (정책 결정, 사용자 승인 필요)**: Phase 1이 게이트를 통과하면 `research/STRATEGY_ENGINE_POLICY.md`의 Track B 절에 확정 규칙(유니버스, 형성 기간, 리밸런싱 주기, buy/hold 이원 기준, 비용 가정)을 명문화하고 사용자 승인을 받는다. 통과하지 못하면 "추가 검증 필요/채택 비추천"으로 기록하고 Phase 0을 유지한다.
- **Phase 3 (구현, Codex 통합 + Claude 격리 구현)**: 승인된 규칙만 `TradingStrategy` 구현체(예: Track A의 `WeeklyMaCrossoverStrategy`에 대응하는 새 컴포넌트)로 옮긴다. `StrategySignal`/`StrategyDecision` 분리, 종목별 부분 실패 응답(`unavailableAssets`), 429 요청 제한 처리 패턴을 재사용한다. 고정 주봉 스냅샷 기반 골든 테스트를 Track A와 같은 방식으로 추가한다.
- **Phase 4 (데이터 인프라, Phase 1/3과 병행 가능)**: S&P500 규모 유니버스를 정기적으로 스캔할 수 있는 배치/캐시 설계와 Twelve Data 요금제 처리량 확인을 별도 작업으로 진행한다. 이는 모멘텀 후보뿐 아니라 향후 밸류에이션 후보를 승격시키더라도 공통으로 필요한 선결 작업이다.
- **Phase 5 (선택적 향후 과제)**: 펀더멘털 데이터 제공자 도입을 사용자가 별도로 결정하면, 그때 밸류에이션(②)의 `BUY` 승격과 퀄리티 팩터(④)를 Track B v2 또는 별도 Track C 후보로 재조사한다.

## 실전 적용 시 유의점

- 이 리포트는 문헌 조사와 기존 내부 백테스트 인용을 기반으로 한 비교·권고이며, 모멘텀 후보에 대해 이 프로젝트 고유의 데이터로 직접 계산한 워크포워드 숫자는 아직 없다. "다음 조사 우선순위"라는 표현은 "채택"이 아니다.
- ①(Track A 규칙 재사용)에 쓰인 내부 백테스트는 표본이 종목당 2~5건에 불과해(`trackb-ma-timing-regular-stocks.md` 자체 고지) 통계적 유의성이 낮다. 다만 이 리포트는 그 결과를 "채택 여부"가 아니라 "이 규칙을 Track B에 그대로 복사하지 않는다"는 방향성 근거로만 사용했다.
- 모든 수치는 세금·배당 재투자 타이밍을 반영하지 않았고, 실제 구현 전에는 이 회사의 브로커 수수료·체결 슬리피지 실측치로 다시 검증해야 한다.
- 이 리포트가 제안하는 어떤 규칙도 자동 주문이나 수익 보장을 의미하지 않는다. 최종 판단은 사용자가 하며, `StrategyDecision`과 (아직 없는) Track B `TradePlan` 생성 규칙은 분리해서 다룬다.

## 출처

- Jegadeesh, N. & Titman, S. (1993). *Returns to Buying Winners and Selling Losers: Implications for Stock Market Efficiency*. The Journal of Finance, 48(1), 65–91. https://onlinelibrary.wiley.com/doi/abs/10.1111/j.1540-6261.1993.tb04702.x
- Kenneth R. French Data Library. *Detail for Monthly Momentum Factor (Mom)* (12-1개월 모멘텀 팩터 공식 구성 방법). https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/data_library/det_mom_factor.html
- Asness, C. S., Moskowitz, T. J., & Pedersen, L. H. (2013). *Value and Momentum Everywhere*. The Journal of Finance, 68(3), 929–985. https://www.aqr.com/Insights/Research/Journal-Article/Value-and-Momentum-Everywhere
- S&P Dow Jones Indices. *S&P Momentum Indices Methodology* (공식 방법론). https://www.spglobal.com/spdji/en/methodology/article/sp-momentum-indices-methodology/
- Daniel, K. & Moskowitz, T. J. (2016). *Momentum Crashes*. Journal of Financial Economics, 122(2), 221–247. https://www.nber.org/papers/w20439
- Novy-Marx, R. & Velikov, M. (2016). *A Taxonomy of Anomalies and Their Trading Costs*. The Review of Financial Studies, 29(1), 104–147. https://www.nber.org/papers/w20721
- Lakonishok, J., Shleifer, A., & Vishny, R. W. (1994). *Contrarian Investment, Extrapolation, and Risk*. The Journal of Finance, 49(5), 1541–1578. https://www.nber.org/papers/w4360
- Ang, A., Hodrick, R. J., Xing, Y., & Zhang, X. (2006). *The Cross-Section of Volatility and Expected Returns*. The Journal of Finance, 61(1), 259–299. https://onlinelibrary.wiley.com/doi/10.1111/j.1540-6261.2006.00836.x
- S&P Dow Jones Indices. *S&P 500 Low Volatility Index* 공식 방법론. https://www.spglobal.com/spdji/en/documents/methodologies/methodology-sp-500-low-volatility-index.pdf
- Asness, C. S., Frazzini, A., & Pedersen, L. H. *Quality Minus Junk*. Review of Accounting Studies. https://link.springer.com/article/10.1007/s11142-018-9470-2
- 내부 근거: `research/reports/trackb-ma-timing-regular-stocks.md`, `research/reports/two-track-strategy-framework.md`, `research/reports/relative-valuation-band.md`, `research/reports/analyst-consensus-target-price.md`, `research/reports/track-a-stoploss-revalidation-and-sizing-design.md`, `research/reports/track-a-trailing-stop-review.md`, `research/data/candidates.json`
- 코드 확인: `src/main/java/com/tradeguide/service/strategy/tracka/WeeklyMaCrossoverStrategy.java`, `src/main/java/com/tradeguide/service/strategy/StrategySelector.java`, `src/main/java/com/tradeguide/domain/strategy/InvestmentTrack.java`, `src/main/java/com/tradeguide/domain/market/MarketDataProvider.java` (2026-09-11 기준)

> confidence: low — 외부 학술·공식 방법론 문헌은 충실히 인용했지만, 이 리포트가 제안한 모멘텀 규칙 자체를 이 회사의 실데이터·비용 가정으로 직접 백테스트한 결과는 아직 없다. ①(Track A 재사용)만 내부 실데이터 기각 근거가 있고, ②·④는 데이터 인프라 부재로 실행 가능성 자체를 검증하지 못했다. 이 리포트의 역할은 "무엇을 다음에 검증할지"를 좁히는 것이며, 채택 결정을 대신하지 않는다.
