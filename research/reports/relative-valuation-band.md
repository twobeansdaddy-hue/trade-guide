# 상대가치평가(PER/PBR/PEG/EV-EBITDA) 밴드 기반 목표가 설정법

## 핵심 결론 (3~5줄)

상대가치평가는 DCF처럼 미래 현금흐름을 직접 추정하지 않고, 동종업종·과거 이력 대비 "얼마나 싸거나 비싼가"를 배수(Multiple)로 비교해 목표가를 산정하는 방법이다. PER 밴드 차트가 가장 널리 쓰이며, 기업의 역사적 PER 범위(예: 삼성전자 8.2배~13.2배) 대비 현재 PER의 위치로 저평가/고평가 구간을 판단한다. PEG는 성장률까지 반영해 성장주 밸류에이션에 적합하고(Lynch 기준 PEG<1 저평가, PEG>2 고평가 신호), EV/EBITDA는 부채비중이 크거나 감가상각이 큰 산업·적자 성장기업에 P/E보다 유용하다. 다만 이 방법은 "비교 대상 자체가 고평가/저평가되어 있으면" 같이 왜곡된다는 근본적 한계가 있어, 절대가치평가(DCF)와 반드시 병행 검증해야 한다.

## 방법론 / 근거

**PER(주가수익비율) 밴드**
- PER = 주가 ÷ EPS(주당순이익). 낮을수록 순이익 대비 저평가, 높을수록 고평가 가능성
- PER 밴드 차트: 특정 종목의 과거 수년간 PER 궤적을 그려 역사적 저점~고점 범위(밴드)를 시각화. 현재 PER이 밴드 하단에 가까우면 저평가 구간, 상단에 가까우면 고평가 구간으로 해석
- 목표가 산정: (적정 PER, 통상 역사적 평균 또는 동종업종 평균) × (추정 EPS) = 목표주가
- 동일 업종 내 비교기업(peer group) PER과 비교하는 방법도 병행

**PBR(주가순자산비율)**
- PBR = 주가 ÷ BPS(주당순자산). 자산 대비 주가 수준을 나타내며, 금융업·자산 비중이 큰 업종에 특히 유용
- ROE(자기자본이익률)와 함께 봐야 함 — 동일 PBR이라도 ROE가 높으면 상대적으로 저평가로 해석 가능

**PEG(PER 대비 성장률)**
- PEG = PER ÷ 연간 EPS 성장률(%)
- Peter Lynch가 대중화한 지표: PEG ≈ 1.0을 "적정가치"의 기준선으로 삼고, PEG < 1이면 성장 대비 저평가, PEG > 2이면 성장 기대가 과도하게 선반영된 것으로 해석
- 성장주처럼 PER 자체는 높아도 성장률이 그만큼 높다면 PEG로 보정해 밸류에이션 왜곡을 줄일 수 있음

**EV/EBITDA**
- EV(기업가치, 시가총액+순부채) ÷ EBITDA(이자·세금·감가상각 전 이익)
- 자본구조(부채비중) 차이, 감가상각 정책 차이의 영향을 배제하고 순수 영업성과 기준으로 비교 가능
- 부채비중이 큰 업종(통신, 유틸리티 등), 감가상각이 큰 자본집약적 업종, 이익은 적자이거나 변동성이 큰 고성장기업(EBITDA는 플러스인 경우) 밸류에이션에 P/E보다 적합
- M&A 등 기업 전체가치 비교 시에도 표준적으로 사용됨

**밴드 기반 목표가 산정 공통 절차**
1. 비교 기준 선택 — 자기 종목의 과거 히스토리 밴드 vs 동종업종 peer group 배수 (또는 둘 다 활용)
2. 적정 배수 범위(저평가 구간/평균/고평가 구간) 설정
3. 추정 실적(EPS, BPS, EBITDA 등)에 배수를 곱해 목표주가 밴드(range) 산출
4. 여러 지표(PER/PBR/PEG/EV-EBITDA)로 교차 검증해 하나의 배수에만 의존하지 않도록 함

## 실전 적용 시 유의점

- 상대가치평가는 "비교 대상이 적정하다"는 전제에 의존함 — 업종 전체가 버블이거나 저평가되어 있으면 밴드 자체가 왜곡되어 있을 수 있음. 반드시 DCF 등 절대가치평가와 교차검증할 것
- PER은 이익이 적자이거나 변동성이 큰 기업에는 적용이 어려움 — 이 경우 EV/EBITDA, PSR(매출액 대비) 등 대체 지표 사용
- PEG는 성장률 추정치 자체가 부정확하면 왜곡됨 — 컨센서스 성장률과 실제 실현 성장률의 괴리가 큰 경우가 흔함
- EV/EBITDA는 감가상각·부채 차이를 배제하는 장점이 있지만, CAPEX 재투자 부담을 반영하지 못하는 한계가 있어 자본집약적 업종에서 과대평가 신호를 놓칠 수 있음
- 목표가는 단일 값이 아니라 배수 범위(저평가~고평가 구간)에 기반한 밴드(range)로 제시하고, 여러 지표를 병행해 신호가 일치하는지 확인할 것

## 출처

- [Valuation & Market Ratios: P/E, EV/EBITDA, Yield — Umbrex](https://umbrex.com/resources/financial-ratio-primer/valuation-market-ratios/) (접속: 2026-08-05)
- [Relative valuation conflicts - EV/EBITDA versus P/E — The Footnotes Analyst](https://www.footnotesanalyst.com/relative-valuation-conflicts-ev-ebitda-versus-p-e/) (접속: 2026-08-05)
- [The PEG Ratio: Peter Lynch's Secret to 29% Annual Returns — FAST Graphs](https://www.fastgraphs.com/blog/the-peg-ratio-peter-lynchs-secret-to-29-annual-returns-and-how-fast-graphs-makes-it-easy-to-use) (접속: 2026-08-05)
- [When to use P/E Vs EV/EBITDA in Stock Market — Motilal Oswal](https://www.motilaloswal.com/learning-centre/2020/2/when-to-use-p-e-vs-ev-ebitda-in-stock-market) (접속: 2026-08-05)
- [내 주식이 싼지 비싼지 알 수 있는 방법 : PER vs PBR — 토스피드](https://toss.im/tossfeed/article/per-and-pbr) (접속: 2026-08-05)
- [중고딩도 할 수 있는 적정주가 구하기 — 스탁플러스 인사이트](https://insight.stockplus.com/articles/1037) (접속: 2026-08-05)

> confidence: medium — 지표별 정의·해석 기준은 표준적으로 검증된 내용이나, PEG의 "1.0 기준선"이나 업종별 적정 배수 범위는 시장 상황·업종에 따라 편차가 커 절대적 기준으로 사용하기보다 상대적 참고선으로 활용해야 함.
