# 데이터 소스/가격 조정 방식 불일치 점검 (2026-08-12)

## 배경

구현 측이 프로덕션 전략 엔진(`WeeklyMaCrossoverStrategy`)용 골든 테스트 픽스처를 만들면서 사용한
데이터 소스와, 이 리서치 워크트리에서 수행한 SOXL 백테스트가 서로 다른 데이터 소스/조정 방식을
써서 결과가 정확히 일치하지 않는다는 사실이 확인됐다.

- **프로덕션 소스**: Twelve Data, `interval=1week`, `adjustment=splits` (분할만 조정, 배당 재투자 미반영)
  — 출처: `src/test/resources/fixtures/market/soxl-weekly-twelvedata-2021-2026.metadata.json`
- **리서치 백테스트 소스**: Yahoo Finance, Weekly **Adjusted Close** (분할+배당 조정, `soxl-timing-and-drawdown-backtest.md` 방법론 참고)
  — 대상 레코드: `research/data/backtests.json`의 `soxl-ma-crossover-2021-2026`

교차 시점 대조 결과: 4건 일치(2023-02-27, 2023-10-30, 2023-12-04, 2024-09-09), 1건 불일치
(리서치 2025-07-28 vs Twelve Data 2025-08-04, 1주 차이 — 리서치 쪽이 더 이르게 신호 발생).
이 1건이 세 번째 거래(+481.2%)의 진입 시점이라, 리포트의 핵심 수치(합산 복리수익률 +871.5% 등)가
프로덕션 데이터로는 재현되지 않는다.

## 이번에 남긴 기록 (원본 수치는 수정하지 않음)

1. `research/data/backtests.json` → `soxl-ma-crossover-2021-2026` 레코드에 `data_source_review` 필드 추가
   (조정방식 차이, 교차일 불일치, 영향받는 지표, 권고안, 참고 문서 목록 포함)
2. `research/reports/soxl-timing-and-drawdown-backtest.md` 말미에 "데이터 소스 불일치 참고" 절 추가
3. 이 노트 파일

기록 위치를 이렇게 나눈 이유: `backtests.json`은 앱 개발 쪽에서 그대로 소비될 구조화 데이터이므로
기계적으로 읽을 수 있는 필드가 필요했고(①), 사람이 읽는 리포트에도 같은 경고가 있어야
같은 실수가 반복되지 않으므로(②), 그리고 이번 점검 자체의 범위(다른 리포트까지 감사)는
특정 리포트/레코드 하나에 종속되지 않는 별도 기록이 맞다고 판단해 노트 파일을 새로 만들었다(③).

## research/reports/ 전체 데이터 소스 점검

| 리포트 | 가격 시계열 사용 | 소스명 표기 | 조정방식(adjustment) 표기 | 비고 |
|---|---|---|---|---|
| soxl-timing-and-drawdown-backtest.md | O (MA/RSI 백테스트) | O (Yahoo Finance) | O — "Adjusted Close" | 이번에 프로덕션과 직접 대조된 리포트. 위에 불일치 절 추가함 |
| soxl-volatility-decay.md | O | O (Yahoo Finance) | O — "조정종가(Adjusted Close, 배당/분할 반영)"로 가장 명시적 | 8개 리포트 중 조정방식 표기가 가장 구체적 |
| trackb-ma-timing-regular-stocks.md | O (AAPL/JPM/PG) | O (Yahoo Finance) | O — "Adjusted Close" | 배당 반영 여부까지는 vol-decay만큼 명시적이지 않음 |
| soxl-position-sizing-stoploss.md | O (ATR 계산) | O (Yahoo Finance) | **△ 미표기** — "Weekly OHLC"라고만 되어 있고 조정 여부 불명 | ATR은 보통 조정 여부에 따라 값이 달라짐 — 확인 필요 |
| two-track-strategy-framework.md | 부분 (다른 리포트 종합) | O (Yahoo Finance) | **△ 미표기** — "(Weekly)"만 표기 | 종합 리포트라 원자료는 하위 리포트에 있지만, 이 리포트만 봐서는 조정방식을 알 수 없음 |
| analyst-consensus-target-price.md | X (가격 시계열 백테스트 아님) | 해당없음 | 해당없음 | 애널리스트 목표가 정확도 관련 논문/기사 |
| dcf-target-price.md | X | 해당없음 | 해당없음 | DCF 방법론 자료 |
| relative-valuation-band.md | X | 해당없음 | 해당없음 | PER/PBR/PEG/EV-EBITDA 방법론 자료 |
| README.md | X (템플릿 안내 문서) | 해당없음 | 해당없음 | - |

**결론**: 가격 데이터를 다루는 5개 리포트 모두 소스명(Yahoo Finance)은 명시했으나,
`soxl-position-sizing-stoploss.md`와 `two-track-strategy-framework.md` 2건은
**조정방식(분할만 vs 분할+배당)을 명시하지 않아** 이번 SOXL MA 크로스오버처럼 프로덕션과
비교했을 때 재현 불가능한 수치가 나올 위험이 있다. 나머지 리포트(analyst-consensus,
dcf, relative-valuation)는 가격 시계열 백테스트가 아니라 이 문제와 무관하다.

## 향후 방지책 제안 (적용은 하지 않음 — 사용자 확인 필요)

1. `research/reports/README.md`의 권장 리포트 구조("출처" 절)에 **가격 데이터를 다루는 리포트는
   provider, interval, adjustment를 필수로 명시**하도록 템플릿 문구 추가.
2. `research/data/backtests.schema.json`에 가격 데이터 기반 레코드(`type: backtest`)에 한해
   `data_source` 객체(예: `{ "provider": string, "interval": string, "adjustment": string }`)를
   `required`에 준하는 필드로 추가 — 이 스키마는 앱 개발 쪽에서 그대로 소비되므로 변경 전 사용자 승인 필요.
3. 새 백테스트를 남길 때, 가능하면 프로덕션이 실제로 쓰는 소스(Twelve Data, adjustment=splits)를
   기본값으로 쓰고, 부득이 다른 소스를 쓸 경우 그 사실과 재현성 한계를 리포트에 의무적으로 기재.
4. `soxl-position-sizing-stoploss.md`, `two-track-strategy-framework.md`는 위 표에서 확인된
   미표기 항목이므로, 다음에 손댈 기회가 있을 때 조정방식을 확인해 보완.

## 참고

- `src/test/resources/fixtures/market/soxl-weekly-twelvedata-2021-2026.metadata.json` (프로덕션 픽스처, 읽기 전용으로만 참고함)
- `research/data/backtests.json` → `soxl-ma-crossover-2021-2026.data_source_review`
- `research/reports/soxl-timing-and-drawdown-backtest.md` → "데이터 소스 불일치 참고" 절
