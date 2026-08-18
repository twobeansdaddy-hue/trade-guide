# 감사 기록: `stoploss_backtest_results_yahoo_daily.json`이 현재 스크립트로 재현되지 않음 (2026-08-18)

## 배경

`track-a-stoploss-revalidation-and-sizing-design.md` 작업을 시작하며 기존 손절 백테스트 스크립트
(`research/data/tools/run_stoploss_report.py`, `stoploss_daily_backtest.py`)를 확장하기 전에,
회귀 여부를 확인하려고 `run_stoploss_report.py twelvedata`와 `run_stoploss_report.py yahoo`를
수정 전/후로 각각 다시 실행해 기존 캐시 결과와 바이트 단위로 비교했다.

- `research/data/cache/stoploss_backtest_results.json`(Twelve Data 소스): 재실행 결과가 기존 파일과
  **완전히 동일**했다. 문제 없음.
- `research/data/cache/stoploss_backtest_results_yahoo_daily.json`(Yahoo 소스): 재실행 결과가 기존
  파일과 **달랐다**. 원인을 확인한 결과, 디스크에 저장된 기존 파일은 `candidate_configs`가 2개
  (`fixed_25pct`, `atr14_x2_cap35pct`)뿐이고 `daily_execution_source` 필드가 없는, **`run_stoploss_report.py`가
  현재 갖고 있는 5개 후보 구성(`fixed_25pct`, `atr14_x2_cap35pct`, `atr14_x3_cap40pct`, `fixed_30pct`,
  `fixed_20pct`) 이전 버전의 스크립트로 생성된 결과물**이었다. 즉 스크립트는 그 이후 5개 후보로
  확장됐지만, `yahoo` 인자로의 재실행이 그 확장 이후 한 번도 수행되지 않아 캐시가 스크립트 최신
  버전과 어긋난 상태로 남아 있었다.

## 조치

- **원본 파일은 조용히 덮어쓰지 않았다.** 재실행 직후 바로 원본으로 복원했다
  (`diff` 확인 후 `RESTORED OK`). 현재 `research/data/cache/stoploss_backtest_results_yahoo_daily.json`은
  이 감사 시점 이전과 동일한 내용이다.
- 이번 신규 작업(`track-a-stoploss-revalidation-and-sizing-design.md`)은 이 파일에 의존하지 않는다 —
  새 분석은 별도 스크립트(`research/data/tools/run_stoploss_report_v2.py`)와 별도 출력 파일
  (`research/data/cache/stoploss_backtest_results_extended.json`)을 사용해 기존 두 캐시 파일을
  전혀 건드리지 않는다.

## 영향 범위

- `research/reports/track-a-stoploss-drawdown-review.md`(직전 리포트)의 "손절 후보 비교" 표는
  본문에서 명시한 대로 **Twelve Data 소스가 주 결과**이고, Yahoo 소스는 20개 사이클의
  진입일·진입가·수익률 교차검증(4개 후보 비교 표 자체가 아니라 "표본" 절의 사이클 목록 대조)에만
  쓰였다. 즉 이번에 발견된 캐시 불일치가 그 리포트의 핵심 수치(손절 후보 비교 표)에 직접 영향을
  주지는 않는다.
- 다만 `stoploss_backtest_results_yahoo_daily.json` 파일 자체는 **현재 시점 기준으로
  `run_stoploss_report.py yahoo`를 재실행하면 재현되지 않는다** — 파일에 `fixed_30pct`,
  `fixed_20pct`, `atr14_x3_cap40pct` 후보 결과가 아예 없다. 이 3개 후보를 Yahoo 소스로 인용해야 하는
  후속 작업이 있다면, 이 파일을 그대로 신뢰하지 말고 `python3 research/data/tools/run_stoploss_report.py yahoo`를
  다시 실행해 최신 캐시로 갱신해야 한다(단, 그 실행 자체는 이번 세션 범위 밖이라 수행하지 않았다 —
  실행하면 파일 내용이 바뀌므로, 실행 여부와 시점은 사용자/Codex가 별도로 판단해야 한다).

## 재현 방법 (문제를 직접 확인하고 싶을 때)

```bash
cp research/data/cache/stoploss_backtest_results_yahoo_daily.json /tmp/before.json
python3 research/data/tools/run_stoploss_report.py yahoo
diff /tmp/before.json research/data/cache/stoploss_backtest_results_yahoo_daily.json
# 차이가 나면(=atr14_x3_cap40pct, fixed_30pct, fixed_20pct 관련 블록이 새로 생기면) 이 노트의 관찰과 일치
cp /tmp/before.json research/data/cache/stoploss_backtest_results_yahoo_daily.json  # 원복
```

## 참고

- `research/data/tools/run_stoploss_report.py` (읽기 전용 대조 대상, 이번에 수정하지 않음)
- `research/data/cache/stoploss_backtest_results_yahoo_daily.json` (문제의 파일, 원본 그대로 보존)
- `research/reports/track-a-stoploss-revalidation-and-sizing-design.md` (이번 신규 리포트, 이 파일에
  의존하지 않고 별도 산출물 사용)
