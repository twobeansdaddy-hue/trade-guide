# 전략 엔진 근거 검토 — 다음 엔진 후보군 비교와 백테스트 우선순위

- 작성일: 2026-09-03
- 작업 계약: `docs/agent-tasks/claude-strategy-engine-evidence-review.md`
- 성격: **리서치 문헌·데이터 검토**다. 이 문서는 전략을 채택하지 않고, 정책 파일(`research/STRATEGY_ENGINE_POLICY.md`)이나
  구현(`src/**`)을 바꾸지 않는다. 아래 "정책 문구 초안"도 **초안**이며 사용자의 명시적 채택 전에는 효력이 없다.
- 이 문서는 예측이나 수익을 약속하지 않는다. 모든 결론은 "무엇을 다음에 검증할 가치가 있는가"에 대한 판단이다.

## 0. 3분 요약

1. 현재 Track A 규칙(주봉 10/40 이동평균 + `weeksSinceCross <= 4`)은 학문적으로 **시간축 모멘텀(time-series
   momentum) / 추세 추종** 계열에 속한다. 이 계열은 이 문서가 검토한 5개 전략군 중 **1차 자료의 뒷받침이 가장
   두껍고**(100년 이상·58개 자산 표본), 동시에 **가장 강한 반증 논문도 존재하는**(자산별 회귀에서는 유의성이
   사라진다) 계열이다. 즉 기준선 자체를 버릴 근거는 없지만, "10/40 주봉"이라는 특정 파라미터가 옳다는 근거도 없다.
2. **횡단면 모멘텀**과 **가치·품질·수익성 선별**은 학술 근거는 두껍지만, 이 프로젝트에서는 **실행 가능한 데이터가
   없다**. 백테스트에 필요한 "시점 기준(point-in-time) S&P 500 구성종목 이력"이 무료로 확보되지 않고, 현재
   `research/data/candidates.json`이 사용한 Finviz 현재 구성종목 목록은 생존 편향과 선견 편향을 그대로 안는다.
   작업 계약의 "실행 가능한 데이터가 없는 후보는 구현 후보로 추천하지 않는다" 기준에 따라 **둘 다 백테스트
   후보에서 제외**한다(연구 자체를 부정하는 것이 아니라, 지금 이 저장소에서 정직하게 검증할 수 없다는 뜻이다).
3. **변동성 관리**는 이 프로젝트에 특히 잘 맞는다. 근거가 "샤프 개선"이 아니라 **"좌측 꼬리(극단 손실) 완화"**
   쪽에서 가장 견고하고, 대상이 3배 레버리지 ETF이며, 무엇보다 현재 정책이 막혀 있는 `quantityRatio` 자동 산출에
   **손절폭을 입력으로 요구하지 않는 대안 입력**을 제공한다. 데이터도 이미 저장소에 있다.
4. **이벤트 위험 회피(실적·거시)**는 Track A(ETF)에는 실적 발표 자체가 없어 적용 대상이 아니고, Track B에는
   적용 가치가 있지만 **수익 규칙이 아니라 경고·표시 기능**으로만 근거가 선다(문헌은 오히려 실적 발표 구간의
   평균 수익률이 **양수**라고 보고한다 — 회피하면 수익을 버린다). 백테스트 후보가 아니라 데이터 보강 항목이다.
5. RSI, MACD, 볼린저 밴드, 분할 매수·매도는 **전부 "채택 보류"**다. 이 저장소의 자체 백테스트(RSI 2건 표본)와
   외부 1차 자료(데이터 스누핑 보정 후 거래비용을 넣으면 초과수익이 사라짐)가 같은 방향을 가리킨다.
6. **다음에 백테스트할 후보는 정확히 2개**다: (A) Track A 신호 산출 기준을 레버리지 ETF에서 **기초 지수 ETF**로
   옮기는 안(표본을 20 → 75 사이클로 늘리고 2001·2008 위기를 처음으로 표본에 넣는다), (B) **변동성 타깃 기반
   포지션 사이징**. 두 후보 모두 사전 고정 계획을 아래 6절에 기록했고, 사후 파라미터 탐색을 명시적으로 금지한다.
7. **`research/data/backtests.json`은 이번에 수정하지 않았다.** 이 문서는 실측을 수행하지 않았으므로 기록할
   결과가 없고, 현재 스키마에는 "사전 등록(pre-registration)" 레코드 타입이 없다. 스키마를 임의로 넓히는 대신
   사전 등록 내용을 이 리포트에 고정하고, 스키마 확장은 8.3절에 **제안**으로만 남긴다.

---

## 1. 기준선 — 현재 Track A 규칙과 기존 리서치의 범위·한계

### 1.1 현재 구현되어 있는 것 (읽기 전용 확인)

`src/main/java/com/tradeguide/service/strategy/tracka/WeeklyMaCrossoverStrategy.java`와
`src/main/java/com/tradeguide/service/strategy/StrategyDecisionMaker.java`를 직접 읽어 확인한 현재 규칙:

| 항목 | 현재 값 |
|---|---|
| 전략 ID / 버전 | `track-a-weekly-ma-crossover` / `v1` |
| 입력 | 완료 주봉 종가 (최소 41봉) |
| 추세 판정 | `SMA(10) > SMA(40)` → `ABOVE_LONG_AVERAGE`, 아니면 `BELOW_LONG_AVERAGE` |
| 이벤트 판정 | 직전 봉과 현재 봉의 대소 반전으로 `CROSS_UP` / `CROSS_DOWN` |
| `weeksSinceCross` | 최근 교차 이후 경과 주 수(교차가 없으면 `null`) |
| 보유 종목 행동 | `ABOVE_LONG_AVERAGE` → `HOLD`, 그 외 → `SELL` |
| 미보유 후보 행동 | `ABOVE_LONG_AVERAGE && weeksSinceCross != null && weeksSinceCross <= 4` → `BUY`, 그 외 → `WATCH` |
| 손절 / 수량 | **없음.** `TradePlanGenerator` 미구현, `quantityRatio` 자동 산출 보류 |
| 적용 범위 | `TRACK_A`만. 신뢰도 `low-medium` |

### 1.2 이미 수행된 Track A 리스크 종료 리서치의 범위와 결론

| 리포트 | 검증 대상 | 결론 | 결정적 한계 |
|---|---|---|---|
| `track-a-stoploss-drawdown-review.md` | 고정 비율 `-20/-25/-30%` 손절 | 추가 검증 필요 | SOXL/TQQQ 20사이클, 수수료·슬리피지 미반영, 워크포워드 없음 |
| `track-a-stoploss-revalidation-and-sizing-design.md` | 위 항목을 4종 ETF 49사이클 + 편도 슬리피지 0.15%p + 워크포워드로 재검증 | **추가 검증 필요 유지** | 워크포워드 분할 시점(2019 vs 2020)에 따라 결론 방향이 뒤집힘 |
| `track-a-trailing-stop-review.md` | 종가 고점 대비 `-20/-25/-30%` 추적 손절 | **채택 비추천** | 4분할 워크포워드·중복 제거 표본 전부에서 비용/휩쏘율 열위 |
| `track-a-market-regime-filter-review.md` | SPY 40주선 시장 국면 진입 필터 | **추가 검증 필요**(효과를 관측할 표본이 0건) | 20사이클 전부에서 필터가 한 번도 발동하지 않음 |
| `track-a-entry-delay-cutoff-review.md` | 진입 지연 컷오프 | `weeksSinceCross <= 4` **채택** | 명목 20사이클이나 독립 표본은 7~8개 수준 |

### 1.3 다섯 리포트가 공유하는 **같은 한 가지 한계**

다섯 리포트 모두 결론의 신뢰도를 `low` 또는 `low-medium`으로 낮추는 이유가 사실상 동일하다.

> **표본이 SOXL·TQQQ(+TNA·FAS) 2010년 이후 구간에 갇혀 있고, 그 안의 사이클들이 같은 거시 충격
> (2020 코로나, 2022 긴축, 2023~ AI 상승장)에 동시에 반응해 통계적으로 독립적인 에피소드는 7~8개 수준이다.**

즉 **지금 Track A 리서치의 병목은 "어떤 규칙을 더 시도할까"가 아니라 "표본이 없다"**이다. 손절 변형을 네 번째,
다섯 번째로 더 시도해도 같은 20~49개 사이클 위에서 돌면 신뢰도는 올라가지 않는다. 이 진단이 아래 6절에서
백테스트 후보 A를 1순위로 놓은 이유다.

---

## 2. 전략군별 근거 평가

각 전략군을 독립 후보로 평가한다. 지표를 조합하거나 결과를 본 뒤 파라미터를 조정하지 않는다.
각 절은 **지지 근거 → 반증 근거 → 이 프로젝트에서의 적용 가능성** 순서다.

### 2.1 시간축 모멘텀 / 추세 추종 (time-series momentum, trend following)

**지지 근거**
- Moskowitz, Ooi, Pedersen (2012), *Time Series Momentum*, JFE 104(2):228–250. 주가지수·통화·상품·채권 선물 58개
  유동 자산에서 1~12개월 수익 지속성을 확인했고, 자산군을 가로지른 분산 포트폴리오가 표준 요인으로 설명되지 않는
  초과수익을 냈으며 **극단적 시장 구간에서 가장 잘 작동**했다고 보고한다.
- Hurst, Ooi, Pedersen (2017), *A Century of Evidence on Trend-Following Investing*, JPM 44(1):15–29. 1880년까지
  거슬러 올라가는 신규 데이터로 **1880년 이후 모든 10년 구간에서 시간축 모멘텀의 평균 수익이 양수**였고,
  60/40 포트폴리오의 최대 낙폭 상위 10개 위기 중 8개에서 양호했다고 보고한다.

**반증 근거 (반드시 함께 읽어야 한다)**
- Huang, Li, Wang, Zhou (2020), *Time series momentum: Is it there?*, JFE 135(3):774–794. **자산별 시계열 회귀에서는
  TSM의 증거가 표본 내·외 모두에서 거의 없고**, 풀링 회귀의 큰 t값은 부트스트랩 임계값을 넘지 못한다. 투자 성과
  자체는 양수지만 **"과거 표본 평균"을 쓰는 단순 전략과 사실상 동일**하다고 보고한다. 즉 "추세를 따르는 것"의
  가치와 "지난 12개월 수익률이라는 특정 신호"의 가치는 다른 문제다.

**이 프로젝트에서의 의미**
- 현재 Track A 규칙은 이 계열의 한 사례다. 계열 자체를 폐기할 근거는 없다.
- 그러나 위 반증 논문이 정확히 경고하는 지점("특정 신호 사양이 우연일 수 있다")이 우리 상황과 겹친다. 10/40 주봉은
  이 저장소 어디에서도 **파라미터로서 검증된 적이 없다**(검증된 것은 진입 지연 컷오프뿐이다).
- 데이터는 이미 있다. 그리고 무료 공개 데이터로 **표본을 크게 늘릴 수 있다**(6.1절).

### 2.2 횡단면 모멘텀 기반 후보 선별 (cross-sectional momentum)

**지지 근거**
- Jegadeesh & Titman (1993), JF 48(1):65–91. 과거 3~12개월 상대 성과 상위 종목 매수·하위 종목 매도가 3~12개월
  보유 구간에서 유의한 양의 수익을 냈고, 체계적 위험이나 지연 반응으로 설명되지 않는다.
- Asness, Moskowitz, Pedersen (2013), *Value and Momentum Everywhere*, JF 68(3):929–985. 8개 시장·자산군에서
  가치·모멘텀 프리미엄이 일관되게 나타나며 **둘은 서로 음의 상관**을 가진다.

**반증·비용 근거**
- Daniel & Moskowitz (2016), *Momentum crashes*, JFE 122(2):221–247. 모멘텀은 시장 하락 후 변동성이 높은
  "패닉 국면"에서 **드물지만 길고 깊은 손실 구간**을 겪으며, 이는 부분적으로 예측 가능하다.
- Novy-Marx & Velikov (2016), *A Taxonomy of Anomalies and Their Trading Costs*, RFS 29(1):104–147. 월 회전율이
  50%를 넘는 전략은 **거래비용 반영 후 유의한 순수익을 내는 경우가 드물다**. 횡단면 모멘텀은 고회전 전략이다.
- McLean & Pontiff (2016), JF. 97개 예측 변수의 포트폴리오 수익이 표본 외에서 26%, **논문 발표 후 58% 낮아졌다**.

**이 프로젝트에서의 치명적 제약 — 데이터**
- 횡단면 순위를 매기려면 각 시점에 **그 시점의 유니버스 구성종목**이 필요하다. S&P 500 구성종목 변경 이력은
  S&P Dow Jones Indices의 상업 라이선스 대상이며, 무료 공개 API로 시점 기준 이력이 제공되지 않는다.
  유료 대안(EODHD 등)도 2012년 이후만 재구성 가능하다고 안내한다.
- 현재 `research/data/candidates.json`(`trackb-screen-2026-08-05`)은 Finviz의 **현재 시점 S&P 500 목록**으로
  만들어졌다. 스냅샷 기록 용도로는 문제가 없지만, 이 목록으로 과거를 되돌려 백테스트하면 **생존 편향
  (탈락·상장폐지 종목 소멸)과 선견 편향(오늘의 구성종목을 과거에 알고 있었다고 가정)**이 동시에 발생한다.
- 종목 유니버스를 "현재 상장된 대형주"로 한정한 백테스트는, 위 편향 탓에 **어떤 결과가 나와도 해석할 수 없다.**

**판정: 백테스트 후보에서 제외.** 근거가 부족해서가 아니라 **정직하게 검증할 데이터가 없어서**다.

### 2.3 가치·품질·수익성 기반 후보 선별 (value / quality / profitability)

**지지 근거**
- Fama & French (2015), *A five-factor asset pricing model*, JFE 116(1):1–22. 기존 3요인에 **수익성과 투자** 요인을
  추가한 5요인 모형을 제시한다.
- Novy-Marx (2013), *The other side of value: The gross profitability premium*, JFE 108(1):1–28. 총이익/자산 비율이
  **장부가/시가와 거의 같은 수준의 횡단면 예측력**을 가지며, 가장 크고 유동성 높은 종목들 사이에서도 밸류에이션이
  담지 못한 정보를 추가로 제공한다(2013 Fama-DFA Prize).

**반증 근거**
- Hou, Xue, Zhang (2020), *Replicating Anomalies*, RFS 33(5):2019–2133. NYSE 브레이크포인트와 시가총액 가중을
  적용해 마이크로캡 영향을 줄이면 **452개 아노말리 중 65%가 |t| ≥ 1.96을 넘지 못했고**, 다중 검정 기준
  |t| ≥ 2.78을 적용하면 실패율이 **82%**로 올라간다.
- 앞의 McLean & Pontiff(2016)와 Novy-Marx & Velikov(2016)가 여기에도 그대로 적용된다.

**이 프로젝트에서의 데이터 상황 — 절반은 되고 절반은 안 된다**
- **펀더멘털 원자료는 무료 1차 자료로 확보 가능하다.** SEC EDGAR XBRL API(`data.sec.gov`)의 `companyfacts`,
  `companyconcept`, `frames` 엔드포인트가 인증 없이 us-gaap 태그 단위 재무 수치를 제공한다(`frames`는 매일
  새벽 3시경 ET 일괄 갱신). 총이익·자산 같은 Novy-Marx 지표를 **공시 원본에서** 계산할 수 있고, 공시 접수일이
  함께 오므로 **시점 기준(point-in-time) 처리도 원리적으로 가능하다**.
- **그러나 유니버스 문제가 2.2절과 완전히 동일하게 남는다.** "S&P 500 중 상위 N개"를 과거 시점으로 재구성할 수
  없으면, 펀더멘털이 아무리 정확해도 백테스트 결과는 생존 편향으로 오염된다.
- 현재 Track B 정책의 `PEG > 0 && PEG < 1`은 **후보 발굴 조건으로만** 쓰이고 매수 신호가 아니므로(정책 문서
  Track B 절), 지금의 스크리닝 스냅샷 용도로는 이 제약이 문제가 되지 않는다. 문제가 되는 것은 "이 필터가
  수익을 냈는가"를 과거 데이터로 주장하려 할 때다.

**판정: 백테스트 후보에서 제외(현 시점).** 단, **데이터 보강 항목으로는 우선순위가 높다** — EDGAR 기반
펀더멘털 수집은 라이선스 위험이 없고, 유니버스 문제와 독립적으로 Track B 후보 카드의 근거 표시 품질을 올린다.

### 2.4 변동성 관리 / 시장 국면 필터

**지지 근거**
- Harvey, Hoyle, Korgaonkar, Rattray, Sargaison, Van Hemert (2018), *The Impact of Volatility Targeting*,
  JPM 45(1):14–33. 1926년까지 거슬러 올라가는 60개 자산, 목표 변동성 10%. **주식·크레딧 같은 위험자산에서는
  변동성 타깃이 샤프를 개선**했고(채권·통화·상품에서는 미미), **모든 자산군에서 극단적 수익(좌측 꼬리)의
  발생 가능성을 낮췄다** — 좌측 꼬리 사건이 대개 고변동성 국면에 발생하는데 그때 노출이 이미 줄어 있기 때문이다.
- Moreira & Muir (2017), *Volatility-Managed Portfolios*, JF 72(4):1611–1644. 변동성이 높을 때 위험을 줄이는
  관리 포트폴리오가 시장·가치·모멘텀·수익성 등 여러 요인에서 알파와 샤프를 개선했다고 보고한다.

**반증 근거 (중요 — Moreira-Muir 쪽은 그대로 믿으면 안 된다)**
- Cederburg, O'Doherty, Wang, Yan (2020), *On the performance of volatility-managed portfolios*, JFE. 103개 주식
  전략으로 직접 비교하면 변동성 관리 포트폴리오가 체계적으로 우월하지 않고, **스패닝 회귀가 함의하는 전략은
  실시간으로 실행 불가능**하며, 합리적인 표본 외 버전은 원래 포트폴리오보다 **낮은 샤프와 확실성등가수익**을
  냈다. 원인은 스패닝 회귀의 구조적 불안정성이다.
- 정리하면: **"샤프가 좋아진다"는 주장은 표본 외에서 무너지지만, "극단 손실이 덜 심해진다"는 주장은 살아남는다.**
  후자(Harvey et al. 2018)가 이 프로젝트가 기대야 할 근거다.

**레버리지 ETF 맥락의 추가 근거**
- Cheng & Madhavan (2009), *The Dynamics of Leveraged and Inverse Exchange-Traded Funds*, JOIM. 레버리지 ETF는
  기초 지수에 대한 **경로 의존적 옵션이 내재**되어 있어, 변동성이 높으면 중간값 투자자는 장기적으로 가치 침식을
  겪는다. 이 저장소의 `soxl-volatility-decay.md`가 같은 메커니즘을 실데이터로 확인했다.
- 즉 Track A 자산은 **변동성이 높을 때 노출을 줄이는 규칙의 이론적 근거가 가장 강한 자산군**이다. 변동성이
  손실의 크기뿐 아니라 **보유 그 자체의 기대 비용**을 키우기 때문이다.

**이 프로젝트에서의 의미 — 막힌 곳을 뚫는다**
- 현재 정책은 `TradePlanGenerator`를 구현하지 못한다. 이유는 `quantityRatio` 산식의 분모(손절폭)가 없고,
  Track A 손절 규칙이 셋 다 채택되지 않았기 때문이다.
- 변동성 타깃 사이징은 **손절폭을 입력으로 요구하지 않는다**. 입력은 실현 변동성 하나이고, 이 값은 이미
  저장소에 있는 일봉 데이터로 계산된다. 손절 규칙 채택 여부와 **독립적으로** 수량 비율의 근거를 만들 수 있다.
- 다만 이것이 `TradePlan.stopLossPrice` 필수 제약 문제를 해결하지는 **않는다**. 그 결정은 여전히 별도 설계
  판단이다(정책 문서 "현재 구현 우선순위" 1번). 이 점을 혼동하지 않아야 한다.

**판정: 백테스트 후보로 채택(후보 B).**

### 2.5 이벤트 위험 회피 (실적·거시 발표) 를 위한 데이터 보강

**지지 근거 — 그런데 방향이 반대다**
- Frazzini & Lamont (2007), *The Earnings Announcement Premium and Trading Volume*, NBER WP 13090. 예정된 실적
  발표일 **전후로 주가는 평균적으로 상승**하며, 이 "실적 발표 프리미엄"은 크고 견고하다. 월간 전략 기준 연
  7~18% 초과수익, 다른 유명 아노말리보다 높은 샤프를 보고한다.
- 따라서 **"실적 발표 전에 무조건 피한다"는 규칙은 문헌상 수익을 버리는 쪽**이다. 이벤트 회피를 수익 규칙으로
  제안할 근거가 없다.

**그럼에도 이벤트 데이터가 필요한 이유 (제품 관점)**
- Trade Guide는 수익 극대화 엔진이 아니라 **장 시작 전 사용자가 검토하는 의사결정 지원 도구**다. "이 종목은
  내일 장 마감 후 실적 발표가 있다"는 사실은 **수익 신호가 아니라 사용자가 알아야 할 위험 맥락**이다.
- 현재 Track B 정책도 이미 "실적 발표일 표시, 실적 발표 임박 종목은 경고 또는 제외"를 요구한다. 즉 이 항목은
  새 전략이 아니라 **기존 정책이 요구하는 데이터를 실제로 확보하는 일**이다.

**데이터 상황**
- Track A(SOXL/TQQQ/TNA/FAS)는 ETF이므로 **실적 발표 자체가 없다.** 이 항목은 Track A와 무관하다.
- Track B: 프로덕션 제공자인 Twelve Data에 `earnings`, `earnings_calendar` 엔드포인트가 존재하지만 **요금제별
  제공 범위가 다르므로**, 실제 사용 가능 여부는 이 프로젝트의 계약 요금제를 확인해야 한다(이 리서치 역할은
  API 키·계정 정보에 접근하지 않으므로 확인하지 않았다 — 미해결 항목).
- 대안 1차 자료: SEC EDGAR. 실적은 8-K(Item 2.02)와 10-Q/10-K로 공시되며 `data.sec.gov` 제출 이력 API로
  **접수 시각과 함께** 무료 확보된다. 단 이는 **발표 후** 자료이므로 "예정일 사전 경고"에는 그대로 쓸 수 없다.
- 거시 발표(FOMC, CPI 등) 일정은 발행 기관(연준, BLS)이 공개 캘린더로 제공하지만, 이를 매매 규칙으로 쓰는 것은
  이 문서가 권고하지 않는다(위 Frazzini-Lamont 논지와 같은 이유로 근거가 없다).

**판정: 백테스트 후보 아님. 데이터 보강 항목(표시·경고 전용)으로 분류.**

---

## 3. 전략군 비교표

### 3.1 실행 규칙과 데이터 요구사항

| 전략군 | 실행 규칙(요약) | 필요한 원천 데이터 | 적용 트랙 |
|---|---|---|---|
| 시간축 모멘텀 / 추세 추종 | 완료 봉 기준 추세 상태로 진입·청산. 현재 엔진의 10/40 주봉이 이 계열 | 대상 자산 또는 **기초 지수**의 장기 주봉 종가(조정 방식 명시) | Track A (Track B 보조 타이밍으로 확장 가능) |
| 횡단면 모멘텀 | 유니버스 내 상대 과거 수익 순위 상·하위 선별, 주기적 리밸런싱 | **시점 기준 유니버스 구성종목 이력** + 전 종목 가격 이력 | Track B |
| 가치·품질·수익성 | 총이익/자산, B/M, PEG 등으로 후보 선별 | **시점 기준 유니버스** + 공시 원본 재무제표(접수일 포함) | Track B |
| 변동성 관리 / 국면 필터 | 실현 변동성이 목표를 넘으면 노출 축소(또는 시장 국면이 나쁘면 신규 진입 보류) | 대상 자산 **일봉** 수익률(변동성 추정), 국면 필터는 시장 지수 주봉 | Track A 우선 |
| 이벤트 위험 회피 | 매매 규칙 아님 — 예정 이벤트를 카드에 표시·경고 | 실적 예정일 캘린더, 공시 접수 시각 | Track B (Track A ETF는 해당 없음) |

### 3.2 검증 가능성·영향·위험

| 전략군 | 백테스트 가능성(현 저장소) | 향후 Flutter 공유 API·도메인 영향 | 과최적화 위험 | 생존 편향 위험 | 데이터 라이선스 위험 |
|---|---|---|---|---|---|
| 시간축 모멘텀 | **가능(높음)**. 로컬 캐시 + 무료 Yahoo 주봉으로 즉시 | 없음~작음. `StrategySignal` 계약 유지, 신호 산출 입력만 변경 | **중** — 파라미터(10/40, 지연 4주)가 검증되지 않음. 사전 고정으로 통제 필요 | **낮음** — ETF/지수는 상장폐지 편향이 거의 없음 | **낮음** — Yahoo 공개 차트 API, Twelve Data 계약분 |
| 횡단면 모멘텀 | **불가**. 시점 기준 유니버스 없음 | 중 — 후보 랭킹 API와 리밸런싱 주기 개념 신규 도입 | 높음 (룩백·보유기간·상위 N 조합이 넓음) | **매우 높음 — 차단 요인** | **높음** — 지수 구성종목 이력은 상업 라이선스 |
| 가치·품질·수익성 | **부분**. 펀더멘털은 EDGAR로 가능, 유니버스가 불가 | 중 — 펀더멘털 스냅샷 도메인과 기준일 필드 신규 | 중 (지표 선택지가 많음) | **매우 높음 — 차단 요인** | 원자료는 **낮음**(EDGAR 공개), 유니버스는 **높음** |
| 변동성 관리 | **가능(높음)**. 로컬 일봉 캐시로 즉시 | 중 — `quantityRatio` 산출 입력이 생김. `TradePlan` 계약 자체는 불변 | 중 (목표 변동성·룩백·상하한 조합). 사전 고정 필수 | 낮음 | 낮음 |
| 이벤트 위험 회피 | 해당 없음(수익 규칙 아님) | 중 — 카드 응답에 이벤트 경고 필드 추가, 웹·앱 공통 | 해당 없음 | 해당 없음 | **미확인** — Twelve Data 요금제 범위 확인 필요 |

### 3.3 API·도메인 영향에 대한 공통 주의

- 후보 A·B 모두 **`StrategySignal` / `StrategyDecision` / `TradePlan`의 기존 계약을 바꾸지 않아도 검증 가능**하다.
  후보 A는 신호 계산의 **입력 시계열**만 달라지고, 후보 B는 아직 아무 곳에도 연결되지 않은 `quantityRatio`의
  **근거 입력**을 만든다.
- 다만 후보 A가 만약 채택된다면 `AssetProfile`에 "신호 산출용 기준 심볼"이라는 개념이 필요해진다(예: SOXL의
  신호 기준은 SOXX). 이는 **웹·Flutter 공통 도메인 변경**이므로 채택 시 별도 설계 결정이 필요하다. 이 문서는
  그 필드를 설계하거나 제안하지 않는다 — 백테스트 결과가 나온 뒤의 문제다.

---

## 4. RSI · MACD · 볼린저 밴드 · 분할 매매 — 근거 점검

작업 계약 4항에 따라, 사용자가 언급했던 기법들의 기존 근거가 충분한지 별도로 정리한다.

| 기법 | 이 저장소의 자체 실측 | 외부 1차 자료 근거 | 판정 |
|---|---|---|---|
| **RSI** (14주 과매도<30 매수 / 과매수>70 매도) | `backtests.json`의 `soxl-rsi-meanrev-2021-2026`: 거래 **2건**, 승률 50%, 전략 수익률 +67.4% vs 단순 보유 +127.6%, 전략 MDD **-75.4%**(단순 보유보다 나쁨). confidence `low` | 아래 공통 근거(데이터 스누핑·거래비용) | **채택 보류.** 자체 실측이 "단순 보유보다 못함"을 보였고 표본이 2건이라 반증조차 약하다. 어느 쪽으로도 결론 낼 표본이 없다 |
| **MACD** | **없음.** 이 저장소에 MACD 백테스트 기록이 없다 | 아래 공통 근거 | **채택 보류.** 이 프로젝트 데이터로 검증된 바가 전혀 없다. MACD는 두 지수이동평균의 차이이므로 현재 10/40 SMA 추세 규칙과 **같은 정보를 상당 부분 중복**해서 담는다 — 새 정보를 추가한다는 근거가 없다 |
| **볼린저 밴드** | **없음** | 아래 공통 근거. 밴드 폭 자체가 변동성 추정치이므로, 변동성 정보가 필요하다면 2.4절의 변동성 타깃이 근거가 훨씬 두껍다 | **채택 보류** |
| **분할 매수·매도 (예: 3분할)** | **없음** | 분할 진입·청산의 초과수익 근거는 이 검토에서 확인하지 못했다. 분할은 대개 수익률 개선이 아니라 **체결 위험·후회 분산** 목적의 실행(execution) 기법이다. 게다가 각 단계의 비율·가격 기준이 새 자유 파라미터를 만들어 과최적화 표면을 넓힌다 | **채택 보류.** 현행 정책(“각 단계 비율·지정가·잔여 수량 처리와 백테스트 근거를 별도 정의”)을 그대로 유지 |

**공통 근거 — 기술적 지표 전반에 대한 1차 자료**

- Park & Irwin (2007), *What do we know about the profitability of technical analysis?*, Journal of Economic
  Surveys 21(4):786–826. 현대 연구 95편 중 56편이 긍정, 20편이 부정, 19편이 혼재였으나 **대부분이 검정 절차상
  문제(데이터 스누핑, 표본 선택, 거래비용 미반영)를 안고 있다**고 결론짓는다.
- Bajgrowicz & Scaillet (2012), *Technical trading revisited: False discoveries, persistence tests, and transaction
  costs*, JFE 106(3):473–491. DJIA 1897–2011 일봉에 False Discovery Rate를 적용한 결과, **지속성 검정에서 투자자가
  사전에 미래의 최고 규칙을 고를 수 없었고**, 표본 내에서조차 **낮은 거래비용만 넣으면 성과가 완전히 상쇄**됐다.

이 두 자료의 함의는 "기술적 지표는 무조건 안 된다"가 아니라 **"지표를 사후에 골라 낸 성과는 신뢰할 수 없고,
사전에 고정하고 거래비용을 넣어야 한다"**이다. 그래서 아래 6절의 두 후보는 파라미터를 사전 고정하고
슬리피지를 처음부터 포함한다.

**주의:** 위 "채택 보류"는 이 기법들이 틀렸다는 판정이 아니라, **이 프로젝트가 지금 채택을 정당화할 근거를
가지고 있지 않다**는 판정이다. `research/STRATEGY_ENGINE_POLICY.md`의 "학습/조사 후보" 상태를 그대로 유지한다.

---

## 5. 데이터 확보 가능성 감사

`research/data/tools/strategy_engine_evidence_audit.py`가 이 절의 내용을 기계적으로 재확인한다(9절).

| 데이터 | 출처 | 확보 상태 | 근거 |
|---|---|---|---|
| SOXL/TQQQ/TNA/FAS 주봉·일봉 | Twelve Data(프로덕션 일부) + Yahoo 공개 차트 API | **확보됨** — `research/data/cache/` | 기존 리포트에서 사용 중 |
| SPY 주봉 1993~ | Yahoo 공개 차트 API | **확보됨** | `spy-weekly-yahoo-1993-2026.csv` |
| SOXX / QQQ / IWM / XLF 주봉 | Yahoo 공개 차트 API | **확보 가능(2026-09-03 직접 확인)** — SOXX 2001-07-09~, QQQ 1999-03-08~, IWM 2000-05-22~, XLF 1998-12-21~ | 아래 6.1절 |
| 미국 상장기업 재무 원본(총이익·자산 등) | SEC EDGAR XBRL API (`data.sec.gov`) | **확보 가능** — 인증 불필요, `frames`는 매일 03:00 ET경 갱신 | SEC 공식 API 안내 |
| 실적 예정일 캘린더 | Twelve Data `earnings_calendar` | **미확인** — 요금제별 제공 범위 확인 필요(계정 정보 미접근) | Twelve Data 문서 |
| **시점 기준 S&P 500 구성종목 이력** | S&P Dow Jones Indices | **확보 불가(무료)** — 상업 라이선스. 유료 대안도 2012년 이후만 재구성 | 2.2절 |

**직접 확인한 사실(2026-09-03):** Yahoo 공개 차트 API에 `SOXX`, `QQQ`, `IWM`, `XLF`의 주봉 이력을 조회해
위 시작일과 봉 개수를 확인했다. 이 확인은 조회만 수행했고 **`research/data/cache/`에 파일을 쓰지 않았다**
(작업 계약의 허용 파일 범위 밖이기 때문). 후보 A를 실제로 실행할 때는 캐시 저장을 포함한 별도 작업 계약이 필요하다.

---

## 6. 백테스트 우선 후보 (최대 2개) — 사전 등록 계획

> **사전 등록 원칙 (두 후보 공통, 결과를 보기 전에 고정)**
> 1. 아래 명시된 파라미터·기간·유니버스·판정 지표는 **결과를 본 뒤 변경하지 않는다.**
> 2. 실행 중 여러 파라미터를 훑어보고 가장 좋은 것을 고르는 **사후 탐색을 금지한다.** 사양 목록은 아래에 적힌
>    것이 전부이며, 목록에 없는 사양을 추가하려면 **새 리포트로 분리**해 그 사실을 명시한다.
> 3. 슬리피지 편도 **0.15%p**, 수수료 **0%**를 모든 체결에 적용한다(기존 Track A 리포트와 동일한 가정.
>    수수료를 0으로 두는 이유는 실제 브로커 요율이 확정되지 않았기 때문이며, 임의 가정 대신 분리해 둔다).
> 4. 학습(in-sample) 구간과 검증(out-of-sample) 구간을 **시간순으로** 나누고, 워크포워드 분할 시점을 미리 고정한다.
> 5. 판정은 "좋아 보이면 채택"이 아니라 **미리 적은 성공·실패 조건**으로만 한다. 실패하면 실패로 기록한다.
> 6. 두 후보 중 어느 것도, 성공하더라도 **이 리포트만으로 채택되지 않는다.** 채택은 사용자의 명시적 결정과
>    구현·테스트를 거친다.

### 6.1 후보 A (1순위) — Track A 신호 산출 기준을 레버리지 ETF에서 기초 지수 ETF로 이전

**전략군:** 시간축 모멘텀 / 추세 추종 (2.1절)

**가설 (사전 고정, 단 하나):**
> Track A의 10/40 주봉 추세 신호를 **레버리지 ETF 자체 가격**이 아니라 **그 기초 지수를 추종하는 비레버리지
> ETF 가격**으로 계산해도, 사이클 판정(진입·청산 시점)과 그 결과가 실질적으로 동일하다.

**왜 이것이 1순위인가**
- 1.3절에서 진단했듯 Track A 리서치의 병목은 규칙이 아니라 **표본**이다. 이 가설이 지지되면, 신호를 기초 지수에서
  계산하는 것이 정당화되고 **2001년·2008년 위기를 포함한 훨씬 긴 표본**으로 이후 모든 Track A 연구(손절, 변동성,
  국면 필터)를 다시 돌릴 수 있다. 기존 다섯 리포트가 공유하던 한계를 한 번에 완화한다.
- 이론적 근거도 있다. Cheng & Madhavan(2009)에 따르면 레버리지 ETF 가격은 기초 지수에 대한 **경로 의존적** 함수이고
  고변동성 구간에서 가치가 침식된다. 그렇다면 3배 ETF 가격에 건 이동평균은 "추세" 외에 **변동성 감쇠라는 잡음**을
  함께 담는다. 신호를 기초 지수에서 뽑는 편이 이론적으로 더 깨끗하다.
- **새 파라미터를 하나도 도입하지 않는다.** 규칙(10/40, `weeksSinceCross <= 4`)은 그대로다. 바뀌는 것은 입력
  시계열뿐이라 과최적화 표면이 넓어지지 않는다.

**사전 고정 — 유니버스와 매핑 (결과를 본 뒤 추가·교체하지 않는다)**

| Track A 자산 | 기초 지수 추종 ETF | 확인된 주봉 시작일 | 비고 |
|---|---|---|---|
| SOXL (3x 반도체) | **SOXX** | 2001-07-09 | |
| TQQQ (3x 나스닥100) | **QQQ** | 1999-03-08 | |
| TNA (3x 러셀2000) | **IWM** | 2000-05-22 | |
| FAS (3x 금융) | **XLF** | 1998-12-21 | |

**사전 고정 — 기간과 표본**
- 신호 계산 구간: 각 기초 ETF의 상장 이후 전 구간 ~ 2026-08-31(마지막 완료 주봉). 미완결 주봉은 제외한다.
- 예상 표본 크기(신호 계산만으로 사전 확인, **수익률은 계산하지 않음**): 10/40 주봉 `CROSS_UP` 기준
  SOXX 16 · QQQ 21 · IWM 19 · XLF 19 = **총 75 사이클**. 현재 Track A 표본(20 사이클) 대비 약 3.75배이며,
  **2001–02 닷컴 붕괴와 2008–09 금융위기가 처음으로 표본에 포함된다.**
- 이 사이클 개수 확인은 표본 크기(설계 파라미터)를 알기 위한 것이며 **성과 지표는 일절 계산하지 않았다.**

**사전 고정 — 진입·청산·리밸런싱 규칙 (현행 엔진과 동일, 변경 없음)**
- 진입: `SMA(10) > SMA(40)`(= `ABOVE_LONG_AVERAGE`)이고 `weeksSinceCross <= 4`인 첫 주의 종가.
- 청산: 이후 첫 `CROSS_DOWN` 주의 종가. 미청산 사이클은 마지막 완료 주봉을 임시 청산으로 표시하고 **별도 표기**한다.
- 리밸런싱·분할·손절·재진입 규칙 **없음**(이번 범위 아님).
- 체결가에 편도 슬리피지 0.15%p 적용.

**사전 고정 — 학습/검증 분리와 워크포워드**
- 학습 구간: 각 ETF 상장일 ~ **2013-12-31**. 검증 구간: **2014-01-01 ~ 2026-08-31**.
  (분할 시점을 2014-01-01로 고른 이유: 학습 구간에 2001–02와 2008–09 위기가 모두 들어가고, 검증 구간이
  최근 12년 이상 남도록 하기 위함. 결과를 본 뒤 조정하지 않는다.)
- 워크포워드 분할 시점(사전 고정, 4분할): **2006-01-01 / 2010-01-01 / 2014-01-01 / 2018-01-01.**
- 기존 Track A 표본과 겹치는 구간(SOXL/TQQQ 2010~, TNA/FAS 2008~)은 **중복 표본으로 별도 표기**하고,
  중복 제거 표본 결과를 함께 낸다.

**사전 고정 — 성공·실패 판정 지표**

| 판정 | 조건 |
|---|---|
| **지지(사용 정당화)** | 네 쌍 모두의 겹치는 구간(SOXL↔SOXX·TQQQ↔QQQ는 2010~, TNA↔IWM·FAS↔XLF는 2008~)에서 교차 발생 주가 **±1주 이내로 일치하는 비율 ≥ 80%**이고, 사이클별 수익률 차이의 **중앙값 절댓값 ≤ 5%p**이며, 이 두 조건이 4개 워크포워드 분할 전부에서 방향이 뒤집히지 않는다 |
| **기각(사용 불가)** | 일치율 < 60%이거나, 수익률 차이 중앙값 절댓값 > 15%p |
| **추가 검증 필요** | 위 둘 사이. 또는 분할별로 결론 방향이 뒤집힌다 |

- 보조 기록(판정에는 쓰지 않음): 확장 표본 75 사이클의 승률·평균/중앙값 수익률·MDD 분포. **이 보조 수치를 근거로
  10/40이나 4주 컷오프를 바꾸자고 제안하지 않는다** — 그것은 별도 사전 등록이 필요한 다른 질문이다.

**필요 데이터와 실행 가능성:** Yahoo 공개 차트 API 4종(2026-09-03 확인 완료) + 기존 로컬 캐시.
새 캐시 파일 저장이 필요하므로 **`research/data/cache/**` 쓰기 권한을 포함한 별도 작업 계약이 필요하다.**

**알려진 한계 (미리 적어 둔다)**
- SOXX/QQQ/IWM/XLF는 Yahoo 조정종가(분할+배당) 기준이고, 프로덕션 소스(Twelve Data, `splits`)와 조정 방식이
  다르다. `research/notes/data-source-audit-2026-08-12.md`가 기록한 **1주 교차 시점 차이**가 여기서도 발생할 수 있다.
- 기초 ETF도 지수 자체가 아니라 ETF다(추적오차·배당 처리 존재). "지수"라고 부르지 않고 "기초 지수 ETF"로 부른다.
- 2001·2008 구간에는 해당 레버리지 ETF가 **존재하지 않았다.** 그 구간에서 얻는 것은 **신호(사이클) 표본**이지
  실제 레버리지 ETF 수익률이 아니다. 레버리지 수익률을 합성 시뮬레이션하는 것은 이번 범위에서 **명시적으로 제외**한다
  (일일 리밸런싱·차입비용·운용보수 가정이 필요해 새로운 임의 상수를 도입하게 된다).

### 6.2 후보 B (2순위) — Track A 변동성 타깃 기반 포지션 사이징

**전략군:** 변동성 관리 (2.4절)

**가설 (사전 고정, 단 하나):**
> Track A 진입 시점의 **실현 변동성**에 반비례해 포지션 크기를 정하면, 균등 크기 진입 대비 **최대낙폭(MDD)의
> 좌측 꼬리가 완화**되고, 그 대가로 포기하는 수익률이 MDD 개선폭보다 작다.

**왜 이 가설의 형태가 이런가**
- Cederburg et al.(2020)이 보인 대로 **"샤프가 좋아진다"는 주장은 표본 외에서 무너진다.** 그래서 성공 조건을
  샤프가 아니라 **꼬리 위험 완화와 교환비율**로 잡았다 — Harvey et al.(2018)에서 표본 외까지 살아남은 결론이
  정확히 그것이기 때문이다.
- 교환비율(포기 수익률 ÷ MDD 개선폭)이라는 판정 틀은 기존 `track-a-trailing-stop-review.md`와 동일하게 유지해
  **이전 결론들과 직접 비교 가능하게** 한다.

**사전 고정 — 유니버스와 기간**
- 자산: **SOXL, TQQQ, TNA, FAS** (기존 49사이클 확장 표본과 동일. 후보 A의 결과를 기다리지 않는다 —
  두 후보는 독립적으로 실행 가능해야 한다).
- 기간: 각 자산의 로컬 캐시 전 구간(가장 이른 2008년 ~ 2026-08-31 완료 주봉).
- 데이터: `research/data/cache/`의 기존 일봉·주봉 CSV. **신규 수집 불필요.**

**사전 고정 — 규칙 (파라미터는 아래가 전부다)**
- 변동성 추정: 진입 주 직전 **완료된 일봉 60거래일**의 로그수익률 표준편차 × √252 (연율화). 룩백은 **60일 하나만**
  사용한다. 20일·120일 등 대안 룩백은 **이번 범위에서 검증하지 않는다.**
- 목표 변동성: **연 40%** 하나만 사용한다. (레버리지 ETF의 역사적 실현 변동성이 통상 50~100% 구간이라 40%는
  대부분의 국면에서 노출을 1 미만으로 만든다. 이 값은 "최적값"이 아니라 **사전에 하나만 고른 값**이며,
  결과를 본 뒤 조정하지 않는다.)
- 포지션 배수 = `min(1.0, 목표변동성 ÷ 추정변동성)`. **상한 1.0을 두어 레버리지 ETF에 추가 레버리지가 걸리지
  않게 한다.** 하한은 두지 않는다.
- 진입·청산 시점은 **현행 엔진 규칙 그대로**(10/40, `weeksSinceCross <= 4`, `CROSS_DOWN` 청산). 사이클 도중
  재조정(리밸런싱)은 **하지 않는다** — 진입 시점에 한 번만 크기를 정한다(엔진이 주 1회 판단이고, 사이클 중
  재조정은 새 규칙·새 파라미터를 요구하므로 별도 질문이다).
- 체결에 편도 슬리피지 0.15%p 적용.

**사전 고정 — 학습/검증 분리와 워크포워드**
- 기존 Track A 리포트와 동일한 4분할을 그대로 사용한다: **2018-01-01 / 2019-01-01 / 2020-01-01 / 2021-01-01.**
  (기존 결론과 직접 비교 가능하게 하기 위해 분할 시점을 바꾸지 않는다.)
- 각 분할에서 학습 구간과 검증 구간의 결과 방향이 일치하는지 확인한다.

**사전 고정 — 성공·실패 판정 지표**

| 판정 | 조건 |
|---|---|
| **채택 후보** | 전 표본에서 **하위 10% 사이클(최악 낙폭 구간)의 평균 MDD가 5%p 이상 개선**되고, 교환비율(포기 수익률 ÷ MDD 개선폭)이 **1.5 이하**이며, 4개 워크포워드 분할 전부에서 방향이 뒤집히지 않는다 |
| **채택 비추천** | 교환비율이 **3.0 초과**이거나, MDD 개선이 **2%p 미만**이거나, 분할 중 절반 이상에서 순비용(수익만 잃고 MDD는 그대로) |
| **추가 검증 필요** | 위 둘 사이, 또는 분할별 방향이 엇갈림 |

- 보조 기록(판정에는 쓰지 않음): 샤프·소르티노. **Cederburg et al.(2020)을 근거로 샤프 개선을 채택 사유로 쓰지 않는다.**

**이 후보가 성공해도 하지 않는 것 (미리 못 박는다)**
- `quantityRatio` 자동 산출을 **구현하지 않는다.** 이 백테스트는 "변동성 기반 크기 조절이 꼬리 위험을 줄이는가"만
  답한다. 산식을 `PortfolioRiskPolicy`나 `TradePlanGenerator`에 연결할지는 **별도 설계 결정**이다.
- `TradePlan.stopLossPrice` 필수 제약 문제를 **해결하지 않는다.** 이는 정책 문서 "현재 구현 우선순위" 1번의
  독립된 설계 판단으로 남는다.

### 6.3 이번에 후보로 제안하지 **않는** 것과 그 이유

| 검토했으나 제외 | 이유 |
|---|---|
| 횡단면 모멘텀 백테스트 | 시점 기준 유니버스 없음 → 생존·선견 편향으로 결과 해석 불가 (2.2절) |
| 가치·품질 스크린 백테스트 | 위와 동일. 단, EDGAR 펀더멘털 **수집**은 별개로 가치 있음 (2.3절) |
| 손절 규칙 네 번째 변형 | 같은 20~49 사이클 위에서는 신뢰도가 올라가지 않음. 후보 A로 표본을 넓힌 뒤 재검토 (1.3절) |
| SPY 국면 필터 재시도 | 기존 표본에서 발동 0건. 후보 A로 표본을 넓히면 **처음으로 측정 가능해질 수 있음** — 그때 별도 사전 등록으로 |
| 이벤트 회피 매매 규칙 | 문헌이 반대 방향(발표 구간 평균 수익 양수). 표시·경고 기능으로만 (2.5절) |
| RSI/MACD/볼린저/분할 매매 | 4절 — 전부 채택 보류 |

---

## 7. 정책 문구 초안 (제안만 — `research/STRATEGY_ENGINE_POLICY.md`는 수정하지 않았다)

아래는 **사용자가 검토·수정·거부할 수 있는 초안**이다. 이 리포트는 정책 파일을 편집하지 않았다.

### 초안 7.1 — "학습/조사 후보" 표에 근거 상태를 덧붙이는 문구

> RSI, MACD, 볼린저 밴드, 분할 매수·매도는 `학습/조사 후보` 상태를 유지한다. 2026-09-03 근거 검토
> (`research/reports/strategy-engine-evidence-review.md`)에서, RSI는 이 프로젝트의 자체 백테스트(거래 2건)가
> 단순 보유 대비 열위였고, MACD·볼린저·분할 매매는 이 프로젝트의 실측 근거가 전혀 없음을 확인했다.
> 기술적 지표 전반에 대해서는 데이터 스누핑 보정과 거래비용 반영 후 초과수익이 상쇄된다는 1차 자료
> (Park & Irwin 2007; Bajgrowicz & Scaillet 2012)가 있다. **언급이나 인기만으로 채택하지 않는다.**

### 초안 7.2 — Track B 유니버스 백테스트에 대한 제약 문구 (신규)

> Track B의 후보 선별 규칙(PEG, 이동평균, 향후 도입할 수 있는 횡단면 모멘텀·품질 지표)에 대해 **과거 성과를
> 주장하는 백테스트는, 시점 기준(point-in-time) 유니버스 구성종목 이력을 확보하기 전에는 수행하지 않는다.**
> 현재 시점의 구성종목 목록으로 과거를 되돌린 결과는 생존 편향과 선견 편향을 포함하므로, 어떤 결과가 나와도
> 채택 근거로 사용하지 않는다. 유니버스 이력 확보 이전에도 스크리닝 **스냅샷 기록**과 이후 추적은 계속한다.

### 초안 7.3 — Track A 표본 한계에 대한 문구 (신규)

> Track A의 모든 채택·비채택 결정은 2010년 이후 레버리지 ETF 2~4종, 통계적으로 독립적인 거시 에피소드
> 7~8개 수준의 표본 위에 있다. 같은 표본 위에서 규칙 변형을 추가로 시도해도 신뢰도는 올라가지 않는다.
> 새 Track A 규칙을 검증하기 전에 **표본 확장 가능성을 먼저 검토한다.**

### 초안 7.4 — 사전 등록 원칙 (검증과 활성화 조건 절에 추가)

> 새 전략 검증은 **결과를 보기 전에** 기간, 유니버스, 진입·청산 규칙, 파라미터, 수수료·슬리피지 가정,
> 학습/검증 분리, 워크포워드 분할 시점, 성공·실패 판정 조건을 문서에 고정한 뒤 수행한다. 실행 중 여러 파라미터를
> 훑어보고 가장 좋은 것을 고르는 사후 탐색은 금지한다. 사전 등록 목록에 없는 사양을 추가로 검증했다면
> **그 사실을 리포트에 명시**하고 별도 검증으로 분리한다.

---

## 8. 이 리포트가 하지 않은 것 / 열린 결정

### 8.1 하지 않은 것

- 백테스트를 **실행하지 않았다.** 이 문서는 근거 검토와 사전 등록이다. 성과 수치는 후보 A·B의 사이클 개수
  (표본 크기)를 제외하면 새로 계산하지 않았다.
- `research/STRATEGY_ENGINE_POLICY.md`, `src/**`, `frontend/**`, `docs/**`, 설정 파일을 **수정하지 않았다.**
- API 키·계정·개인 포트폴리오 정보에 **접근하지 않았다.**
- 목표가·손절가·수량 비율을 **제안하지 않았다.**

### 8.2 `research/data/backtests.json`을 수정하지 않은 이유

작업 계약은 이 파일을 허용 파일로 두었으나(“수정한다면 새 레코드만 추가”), **이번에는 추가하지 않았다.**

- 이 리포트는 실측을 수행하지 않았으므로 기록할 `metrics`가 없다.
- 스키마의 `type`은 `backtest`(과거 데이터 검증) 또는 `forecast`(예측 후 결과 확인) 두 가지뿐이다. 사전 등록
  계획은 **둘 중 어느 것도 아니다.** 결과가 비어 있는 레코드를 `type: "backtest"`로 넣으면, 이 파일을 그대로
  소비할 앱 개발 쪽에 "결과가 있는 검증"으로 오인될 수 있다(`research/notes/data-source-audit-2026-08-12.md`가
  같은 이유로 기록 위치를 분리한 선례가 있다).
- 따라서 사전 등록 내용은 이 리포트(6절)에 고정하고, 스키마 확장은 아래 제안으로만 남긴다.

### 8.3 제안(적용하지 않음) — 스키마 확장

> `research/data/backtests.schema.json`의 `type` enum에 `"preregistration"`을 추가하고, 그 타입에서는
> `metrics`/`trades`를 비우되 `prereg` 객체(고정 파라미터, 기간, 워크포워드 분할, 성공·실패 조건, 고정 일자)를
> 요구하는 방안. **이 스키마는 앱 개발 쪽이 소비하므로 변경 전 사용자 승인이 필요하다.** 이번에 적용하지 않았다.

### 8.4 열린 결정 / 위험

| 항목 | 상태 |
|---|---|
| Twelve Data 계약 요금제에서 `earnings_calendar` 사용 가능 여부 | **미확인.** 계정 정보 미접근. 확인 주체가 필요 |
| 후보 A 실행에 필요한 `research/data/cache/**` 쓰기 권한 | **미부여.** 별도 작업 계약 필요 |
| 후보 A가 지지될 경우 `AssetProfile`에 "신호 기준 심볼" 개념 도입 여부 | **미결정.** 웹·Flutter 공통 도메인 변경이므로 결과 확인 후 별도 설계 |
| `TradePlan.stopLossPrice` 필수 제약 유지 여부 | **미결정.** 정책 문서 "현재 구현 우선순위" 1번 그대로. 후보 B가 성공해도 이 질문은 남는다 |
| 후보 A·B의 실행 순서 | A를 먼저 권장(표본 확장이 이후 모든 Track A 연구의 신뢰도를 올린다). 다만 두 후보는 서로 독립적이라 병렬 실행도 가능하다 |
| 두 후보 모두 실패할 가능성 | **실재한다.** 그 경우 결론은 "Track A 엔진 v1을 현재 범위로 유지하고, 표본이 늘어날 때까지 새 규칙을 추가하지 않는다"가 된다. 이는 정당한 결과이며 실패로 기록한다 |

---

## 9. 검증 도구

`research/data/tools/strategy_engine_evidence_audit.py` — 이 리포트의 **데이터 확보 가능성 주장을 기계적으로
재확인**하는 감사 스크립트다. 백테스트를 수행하지 않으며 **어떤 파일도 쓰지 않는다.**

```bash
# 로컬 캐시 인벤토리와 후보별 데이터 요구사항 대조 (네트워크 접근 없음)
python3 research/data/tools/strategy_engine_evidence_audit.py

# JSON 출력
python3 research/data/tools/strategy_engine_evidence_audit.py --json

# 외부 소스 접근 가능성까지 확인 (읽기 전용 조회, 파일 저장 없음)
python3 research/data/tools/strategy_engine_evidence_audit.py --probe
```

종료 코드: 선택된 두 백테스트 후보(A·B)의 필수 데이터가 모두 해결되면 `0`, 하나라도 해결되지 않으면 `1`.
`backtests.json`의 기존 레코드 id 13개가 모두 남아 있는지도 함께 확인한다(“새 레코드만 추가” 규칙의 기계적 가드).

---

## 10. 출처

모든 URL은 2026-09-03에 확인했다. 학술 자료는 원 논문·학술지를, 데이터·규제 자료는 발행 기관 1차 자료를 우선했다.

### 시간축 모멘텀 / 추세 추종
1. Moskowitz, T. J., Ooi, Y. H., & Pedersen, L. H. (2012). "Time Series Momentum." *Journal of Financial
   Economics*, 104(2), 228–250. https://www.sciencedirect.com/science/article/pii/S0304405X11002613
   (저자 공개본: https://w4.stern.nyu.edu/facdir/lpederse/papers/TimeSeriesMomentum.pdf)
2. Hurst, B., Ooi, Y. H., & Pedersen, L. H. (2017). "A Century of Evidence on Trend-Following Investing."
   *The Journal of Portfolio Management*, 44(1), 15–29. https://doi.org/10.3905/jpm.2017.44.1.015
   (SSRN: https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2993026)
3. Huang, D., Li, J., Wang, L., & Zhou, G. (2020). "Time series momentum: Is it there?" *Journal of Financial
   Economics*, 135(3), 774–794. https://doi.org/10.1016/j.jfineco.2019.08.004

### 횡단면 모멘텀
4. Jegadeesh, N., & Titman, S. (1993). "Returns to Buying Winners and Selling Losers: Implications for Stock
   Market Efficiency." *The Journal of Finance*, 48(1), 65–91.
   https://doi.org/10.1111/j.1540-6261.1993.tb04702.x
5. Asness, C. S., Moskowitz, T. J., & Pedersen, L. H. (2013). "Value and Momentum Everywhere." *The Journal of
   Finance*, 68(3), 929–985. https://onlinelibrary.wiley.com/doi/10.1111/jofi.12021
6. Daniel, K., & Moskowitz, T. J. (2016). "Momentum crashes." *Journal of Financial Economics*, 122(2), 221–247.
   https://www.sciencedirect.com/science/article/pii/S0304405X16301490

### 가치 · 품질 · 수익성
7. Novy-Marx, R. (2013). "The other side of value: The gross profitability premium." *Journal of Financial
   Economics*, 108(1), 1–28. https://www.sciencedirect.com/science/article/abs/pii/S0304405X13000044
   (저자 공개본: https://mysimon.rochester.edu/novy-marx/research/OSoV.pdf)
8. Fama, E. F., & French, K. R. (2015). "A five-factor asset pricing model." *Journal of Financial Economics*,
   116(1), 1–22. https://www.sciencedirect.com/science/article/abs/pii/S0304405X14002323

### 재현성 · 다중 검정 · 거래비용 (모든 전략군에 공통 적용)
9. Hou, K., Xue, C., & Zhang, L. (2020). "Replicating Anomalies." *The Review of Financial Studies*, 33(5),
   2019–2133. https://academic.oup.com/rfs/article-abstract/33/5/2019/5236964
   (NBER WP: https://www.nber.org/papers/w23394)
10. McLean, R. D., & Pontiff, J. (2016). "Does Academic Research Destroy Stock Return Predictability?"
    *The Journal of Finance*, 71(1). https://onlinelibrary.wiley.com/doi/abs/10.1111/jofi.12365
11. Novy-Marx, R., & Velikov, M. (2016). "A Taxonomy of Anomalies and Their Trading Costs." *The Review of
    Financial Studies*, 29(1), 104–147. https://academic.oup.com/rfs/article-abstract/29/1/104/1844518

### 변동성 관리
12. Harvey, C. R., Hoyle, E., Korgaonkar, R., Rattray, S., Sargaison, M., & Van Hemert, O. (2018). "The Impact
    of Volatility Targeting." *The Journal of Portfolio Management*, 45(1), 14–33.
    https://jpm.pm-research.com/content/45/1/14.abstract
    (SSRN: https://papers.ssrn.com/sol3/papers.cfm?abstract_id=3175538)
13. Moreira, A., & Muir, T. (2017). "Volatility-Managed Portfolios." *The Journal of Finance*, 72(4), 1611–1644.
    https://onlinelibrary.wiley.com/doi/abs/10.1111/jofi.12513
14. Cederburg, S., O'Doherty, M. S., Wang, F., & Yan, X. S. (2020). "On the performance of volatility-managed
    portfolios." *Journal of Financial Economics*.
    https://www.sciencedirect.com/science/article/abs/pii/S0304405X2030132X

### 레버리지 ETF
15. Cheng, M., & Madhavan, A. (2009). "The Dynamics of Leveraged and Inverse Exchange-Traded Funds."
    *Journal of Investment Management*, Q4 2009. https://papers.ssrn.com/sol3/papers.cfm?abstract_id=1539120

### 기술적 분석 전반
16. Park, C.-H., & Irwin, S. H. (2007). "What Do We Know About the Profitability of Technical Analysis?"
    *Journal of Economic Surveys*, 21(4), 786–826.
    https://experts.illinois.edu/en/publications/what-do-we-know-about-the-profitability-of-technical-analysis/
17. Bajgrowicz, P., & Scaillet, O. (2012). "Technical trading revisited: False discoveries, persistence tests,
    and transaction costs." *Journal of Financial Economics*, 106(3), 473–491.
    https://www.sciencedirect.com/science/article/abs/pii/S0304405X1200116X

### 이벤트 위험
18. Frazzini, A., & Lamont, O. A. (2007). "The Earnings Announcement Premium and Trading Volume."
    NBER Working Paper 13090. https://www.nber.org/papers/w13090

### 데이터 소스 (1차)
19. U.S. Securities and Exchange Commission. "EDGAR Application Programming Interfaces."
    https://www.sec.gov/search-filings/edgar-application-programming-interfaces
    (`submissions`, `companyconcept`, `companyfacts`, `frames` — 인증 불필요, `frames`는 매일 약 03:00 ET 갱신)
20. Twelve Data. API Documentation. https://twelvedata.com/docs (프로덕션 시세 제공자. `earnings`,
    `earnings_calendar` 엔드포인트 존재하나 요금제별 제공 범위는 이 리서치에서 확인하지 못함)
21. Yahoo Finance chart API v8 (`https://query2.finance.yahoo.com/v8/finance/chart/{symbol}`) — SOXX/QQQ/IWM/XLF
    주봉 이력 확보 가능성을 2026-09-03에 직접 조회로 확인(파일 저장 없음). 공식 문서화된 API가 아니며 예고 없이
    변경될 수 있다는 한계를 기존 리포트와 동일하게 유지한다.

### 이 저장소 내부 참고 (읽기 전용)
- `research/STRATEGY_ENGINE_POLICY.md`, `research/TASKS.md`
- `research/reports/track-a-entry-delay-cutoff-review.md`, `track-a-stoploss-drawdown-review.md`,
  `track-a-stoploss-revalidation-and-sizing-design.md`, `track-a-trailing-stop-review.md`,
  `track-a-market-regime-filter-review.md`, `soxl-volatility-decay.md`, `soxl-timing-and-drawdown-backtest.md`,
  `two-track-strategy-framework.md`
- `research/notes/data-source-audit-2026-08-12.md`
- `research/data/backtests.json` (`soxl-rsi-meanrev-2021-2026`, `track-a-market-regime-filter-2026`)
- `research/data/candidates.json` (`trackb-screen-2026-08-05`)
- `src/main/java/com/tradeguide/service/strategy/tracka/WeeklyMaCrossoverStrategy.java`,
  `src/main/java/com/tradeguide/service/strategy/StrategyDecisionMaker.java`
