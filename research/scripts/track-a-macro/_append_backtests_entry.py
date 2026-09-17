import json
import os

BASE = os.path.dirname(os.path.abspath(__file__))
path = "/Users/beansdaaddy/Desktop/_Study.nosync/Java/trade-guide/research/data/backtests.json"
results_path = "/Users/beansdaaddy/Desktop/_Study.nosync/Java/trade-guide/research/data/cache/track-a-macro/vix_entry_filter_results.json"

d = json.load(open(path, encoding="utf-8"))
results = json.load(open(results_path, encoding="utf-8"))

entry = {
    "id": "track-a-vix-entry-filter-hypothesis-2008-2026",
    "type": "backtest",
    "ticker": "SOXL,TQQQ,TNA,FAS",
    "strategy_id": "track-a-macro-vix-entry-filter-candidate",
    "data_range": {"start": "2008-01-01", "end": "2026-09-16", "frequency": "weekly"},
    "created_date": "2026-09-17",
    "summary": (
        "Track A 매크로 오버레이 첫 가설: 진입(CROSS_UP delay=0) 시점 VIX 레벨이 높을수록 이후 사이클 수익률이 "
        "나쁘다는 가설을 SOXL/TQQQ/TNA/FAS 42개 유효 사이클로 검증. 피어슨 상관계수 -0.006으로 사실상 무상관. "
        "임계값(VIX>=25, VIX>=30) 기준으로도 방향이 가설과 반대(고VIX 그룹이 오히려 평균 수익률 높음)이나 "
        "표본이 3건/1건뿐이라 이 반대 방향도 신뢰 불가. 진입 시점 VIX 절대 레벨은 예측력이 없다고 판단해 기각."
    ),
    "metrics": results,
    "report_path": "research/reports/track-a-vix-entry-filter-hypothesis.md",
    "sources": [
        {
            "title": "Yahoo Finance - SOXL/TQQQ/TNA/FAS/^VIX Historical Data (Weekly)",
            "url": "https://finance.yahoo.com/quote/%5EVIX/history/",
            "accessed": "2026-09-17"
        }
    ],
    "confidence": "low-medium",
    "caveats": (
        "유효 사이클 42건 중 VIX>=25는 3건, VIX>=30은 1건뿐이라 임계값 비교의 통계적 힘이 약함. "
        "진입 시점 VIX는 당일이 아니라 해당 주 또는 직전 주 종가로 근사. 이번 재수집 데이터가 기존 "
        "market-regime-filter-tna-fas-extension 리포트의 49사이클과 정확히 일치하지 않을 수 있음(세션 "
        "환경 이슈로 기존 캐시 재사용 불가). 진입 시점 VIX 레벨만 검증했고 변화율·청산시점 VIX 등은 "
        "검증하지 않음."
    ),
    "last_updated": "2026-09-17"
}

d.append(entry)
with open(path, "w", encoding="utf-8") as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write("\n")

print("appended, new length:", len(d))
