# Track B 실데이터 워크포워드 1단계 - 실행 스크립트

`docs/agent-tasks/track-b-walk-forward-data-feasibility.md` 작업 계약의 산출물이다.
표준 라이브러리(urllib, json, csv, unittest)만 사용하며 새 의존성을 추가하지 않는다.
`src/**`, `frontend/**`, `docs/**`, DB, 설정, 의존성, Git은 건드리지 않는다.

## 재현 순서

```bash
# 1) 고정 연구 유니버스(DJIA 30종목) 주봉 데이터 수집 (공개 Yahoo Finance v8 chart API)
python3 research/scripts/track-b-walk-forward/fetch_universe.py

# 2) 계산 코어 단위 테스트 (형성기간 수익률, 미래참조 방지, 비용 단조성, 데이터 품질 정책)
cd research/scripts/track-b-walk-forward && python3 -m unittest test_momentum_engine -v && cd -

# 3) 워크포워드 실행 (0/20/40/57bp × 2분할/4분할)
python3 research/scripts/track-b-walk-forward/run_walk_forward.py
```

## 파일 구성

- `fetch_universe.py`: 고정 유니버스·선정 기준·수집 기간을 코드 상단에 명시하고, Yahoo
  Finance 주봉을 수집해 `research/data/cache/track-b-walk-forward/`에 원본 JSON, 정규화
  CSV, 수집 메타데이터를 남긴다.
- `momentum_engine.py`: 횡단면 모멘텀 계산 코어(형성기간 수익률, 리밸런싱 스케줄, 상위/유지
  이원 기준, 동일가중 백테스트, 동일가중 매수후보유 벤치마크)의 순수 Python 재구현.
  `src/main/java/com/tradeguide/service/backtest/CrossSectionalMomentumBacktestEngine`이
  정의한 규칙을 그대로 옮겼으며, Java 코드를 대체하지 않는다.
- `test_momentum_engine.py`: 위 코어의 단위 테스트 10개(합성 fixture). 미래 가격이 과거
  리밸런싱에 영향을 주지 않음, 비용이 클수록 결과가 나빠지거나 같음, 중복/비정렬/이력부족/
  결측 데이터 품질 정책을 검증한다.
- `run_walk_forward.py`: 사전 고정 파라미터(형성기간 52-4주, 분기 리밸런싱, 상위20%/유지
  40%, 비용 0/20/40/57bp, 2분할/4분할 비중첩 기간)로 실제 실행하고
  `research/data/cache/track-b-walk-forward/walk_forward_results.json`에 원자료를 남긴다.

## 재실행 시 주의

- `fetch_universe.py`를 다시 실행하면 실행 시점까지의 최신 주봉을 다시 받으므로, 이전 실행과
  종료일이 달라질 수 있다(수집 시각은 항상 manifest에 기록됨). 유니버스 종목 자체는
  스크립트에 하드코딩돼 있어 바뀌지 않는다.
- 외부 API가 실패하면 해당 종목은 `universe_manifest.json`에 `status=FAILED`와 사유로
  기록되고, CSV에서는 빠진다. `run_walk_forward.py`는 실패한 종목이 있어도 나머지 종목만으로
  실행되지만, 성공한 종목만으로 전체 결론(채택/기각)을 내려서는 안 된다 - 실패 목록을 반드시
  보고서에 함께 기록해야 한다.
