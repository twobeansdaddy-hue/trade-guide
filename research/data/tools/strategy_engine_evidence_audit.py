#!/usr/bin/env python3
"""
strategy_engine_evidence_audit.py

목적
----
`research/reports/strategy-engine-evidence-review.md`가 주장하는 **데이터 확보 가능성**을 기계적으로
재확인한다. 이 스크립트는 백테스트를 수행하지 않고, 수익률·MDD 같은 성과 지표를 계산하지 않는다.
"어떤 전략군을 지금 이 저장소에서 정직하게 백테스트할 수 있는가"만 판정한다.

이 스크립트가 하는 일
---------------------
1. `research/data/cache/`의 로컬 시세 인벤토리를 읽어 심볼/주기/조정방식/커버리지 기간을 정리한다.
2. 리포트가 정의한 전략군별 데이터 요구사항과 인벤토리를 대조해
   `available` / `obtainable` / `blocked` 로 판정한다.
3. 리포트가 백테스트 후보로 선택한 2건(A, B)의 필수 데이터가 전부 해결되는지 확인한다.
4. `research/data/backtests.json`의 기존 레코드 id가 모두 남아 있는지 확인한다
   ("새 레코드만 추가하고 기존 결과를 덮어쓰지 않는다"는 작업 계약 규칙의 기계적 가드).
5. 리포트 파일에 사전 등록(pre-registration)에 필요한 절이 실제로 들어 있는지 구조적으로 확인한다.

이 스크립트가 **하지 않는** 일
------------------------------
- 파일을 쓰지 않는다. `research/data/cache/`에 아무것도 저장하지 않는다(작업 계약의 허용 파일 범위 밖).
- 기본 실행에서 네트워크에 접근하지 않는다. `--probe`를 준 경우에만 읽기 전용 조회를 수행한다.
- 전략을 채택하거나 파라미터를 탐색하지 않는다.

사용 예
-------
    python3 research/data/tools/strategy_engine_evidence_audit.py
    python3 research/data/tools/strategy_engine_evidence_audit.py --json
    python3 research/data/tools/strategy_engine_evidence_audit.py --probe

종료 코드
---------
    0  선택된 백테스트 후보 A·B의 필수 데이터가 모두 해결되고, 무결성 검사도 통과
    1  하나라도 해결되지 않음 (또는 무결성 검사 실패)
"""
import argparse
import csv
import json
import os
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
CACHE_DIR = os.path.join(REPO_ROOT, "research", "data", "cache")
BACKTESTS_JSON = os.path.join(REPO_ROOT, "research", "data", "backtests.json")
REPORT_PATH = os.path.join(REPO_ROOT, "research", "reports", "strategy-engine-evidence-review.md")

# ---------------------------------------------------------------------------
# 리포트 작성 시점(2026-09-03)에 존재하던 backtests.json 레코드 id.
# 이 목록의 항목이 하나라도 사라지면 "기존 결과 덮어쓰기"이므로 실패로 본다.
# 새 id가 추가되는 것은 허용된다.
# ---------------------------------------------------------------------------
KNOWN_BACKTEST_IDS = [
    "soxl-vol-decay-2021-2026",
    "soxl-ma-crossover-2021-2026",
    "soxl-rsi-meanrev-2021-2026",
    "soxl-drawdown-recovery-2021-2026",
    "soxl-forecast-2026-08-05",
    "soxl-atr-position-sizing-2026-08-05",
    "trackb-ma-crossover-aapl-2021-2026",
    "trackb-ma-crossover-jpm-2021-2026",
    "trackb-ma-crossover-pg-2021-2026",
    "track-a-stoploss-drawdown-2026",
    "track-a-stoploss-revalidation-2026",
    "track-a-trailing-stop-2026",
    "track-a-market-regime-filter-2026",
]

# 리포트 6절의 사전 등록이 반드시 담아야 하는 항목 (구조적 확인용)
REQUIRED_REPORT_SECTIONS = [
    "## 6. 백테스트 우선 후보",
    "사전 등록 원칙",
    "### 6.1 후보 A",
    "### 6.2 후보 B",
    "사전 고정 — 학습/검증 분리와 워크포워드",
    "사전 고정 — 성공·실패 판정 지표",
    "## 7. 정책 문구 초안",
    "## 10. 출처",
]

# ---------------------------------------------------------------------------
# 전략군별 데이터 요구사항 (리포트 2~3절과 1:1 대응)
#
# kind:
#   "local_series"  로컬 캐시에 시세 파일이 있어야 함
#   "free_remote"   무료 공개 API로 확보 가능 (파일은 아직 없음)
#   "licensed"      상업 라이선스 필요 -> blocked
# ---------------------------------------------------------------------------
DATA_REQUIREMENTS = {
    "track_a_weekly_leveraged": {
        "label": "Track A 레버리지 ETF 주봉 (SOXL/TQQQ/TNA/FAS)",
        "kind": "local_series",
        "symbols": ["soxl", "tqqq", "tna", "fas"],
        "interval": "weekly",
    },
    "track_a_daily_leveraged": {
        "label": "Track A 레버리지 ETF 일봉 (변동성 추정용)",
        "kind": "local_series",
        "symbols": ["soxl", "tqqq", "tna", "fas"],
        "interval": "daily",
    },
    "market_index_weekly": {
        "label": "시장 지수 주봉 (SPY)",
        "kind": "local_series",
        "symbols": ["spy"],
        "interval": "weekly",
    },
    "underlying_index_weekly": {
        "label": "기초 지수 ETF 주봉 (SOXX/QQQ/IWM/XLF)",
        "kind": "free_remote",
        "probe_symbols": ["SOXX", "QQQ", "IWM", "XLF"],
        "source": "Yahoo Finance chart API v8",
        "note": "2026-09-03 직접 조회로 확보 가능 확인. 실제 실행 시 cache 쓰기 권한이 필요하다.",
    },
    "fundamentals_filings": {
        "label": "공시 원본 재무제표 (총이익/자산 등)",
        "kind": "free_remote",
        "probe_url": "https://data.sec.gov/api/xbrl/companyconcept/CIK0000320193/us-gaap/Assets.json",
        "source": "SEC EDGAR XBRL API (data.sec.gov)",
        "note": "인증 불필요. frames 엔드포인트는 매일 약 03:00 ET 갱신.",
    },
    "point_in_time_universe": {
        "label": "시점 기준 S&P 500 구성종목 이력",
        "kind": "licensed",
        "source": "S&P Dow Jones Indices",
        "note": "무료 공개 소스 없음. 유료 대안도 2012년 이후만 재구성 가능. 생존/선견 편향 차단 요인.",
    },
    "earnings_calendar": {
        "label": "실적 예정일 캘린더",
        "kind": "unverified",
        "source": "Twelve Data earnings_calendar",
        "note": "계약 요금제별 제공 범위 미확인(이 리서치 역할은 API 키·계정에 접근하지 않는다).",
    },
}

STRATEGY_FAMILIES = [
    {
        "id": "time-series-momentum",
        "label": "시간축 모멘텀 / 추세 추종",
        "track": "TRACK_A",
        "requires": ["track_a_weekly_leveraged", "underlying_index_weekly"],
        "selected_as": "후보 A",
    },
    {
        "id": "cross-sectional-momentum",
        "label": "횡단면 모멘텀 후보 선별",
        "track": "TRACK_B",
        "requires": ["point_in_time_universe"],
        "selected_as": None,
    },
    {
        "id": "value-quality-profitability",
        "label": "가치·품질·수익성 후보 선별",
        "track": "TRACK_B",
        "requires": ["point_in_time_universe", "fundamentals_filings"],
        "selected_as": None,
    },
    {
        "id": "volatility-management",
        "label": "변동성 관리 / 시장 국면 필터",
        "track": "TRACK_A",
        "requires": ["track_a_daily_leveraged", "track_a_weekly_leveraged", "market_index_weekly"],
        "selected_as": "후보 B",
    },
    {
        "id": "event-risk-data",
        "label": "이벤트 위험(실적·거시) 데이터 보강",
        "track": "TRACK_B",
        "requires": ["earnings_calendar"],
        "selected_as": None,
    },
]

STATUS_ORDER = {"available": 0, "obtainable": 1, "unverified": 2, "blocked": 3}


def scan_cache():
    """research/data/cache/의 시세 CSV 인벤토리를 만든다. 파일을 쓰지 않는다."""
    inventory = []
    if not os.path.isdir(CACHE_DIR):
        return inventory
    for name in sorted(os.listdir(CACHE_DIR)):
        if not name.endswith(".csv"):
            continue
        path = os.path.join(CACHE_DIR, name)
        entry = {
            "file": os.path.relpath(path, REPO_ROOT),
            "symbol": name.split("-")[0].lower(),
            "interval": "weekly" if "-weekly-" in name else ("daily" if "-daily-" in name else "unknown"),
            "provider": None,
            "adjustment": None,
            "rows": None,
            "start": None,
            "end": None,
        }
        meta_path = path[:-4] + ".metadata.json"
        if os.path.isfile(meta_path):
            try:
                with open(meta_path, encoding="utf-8") as fh:
                    meta = json.load(fh)
                entry["provider"] = meta.get("provider")
                entry["adjustment"] = meta.get("adjustment")
                entry["start"] = meta.get("startDate")
                entry["end"] = meta.get("endDate")
            except (ValueError, OSError):
                pass
        try:
            with open(path, newline="", encoding="utf-8") as fh:
                sample = fh.read(4096)
                fh.seek(0)
                delimiter = ";" if sample.count(";") > sample.count(",") else ","
                rows = list(csv.reader(fh, delimiter=delimiter))
            data_rows = [r for r in rows[1:] if r and r[0].strip()]
            entry["rows"] = len(data_rows)
            if data_rows:
                first, last = data_rows[0][0].strip(), data_rows[-1][0].strip()
                # 파일마다 asc/desc가 다를 수 있으므로 사전순으로 정렬해 기간을 잡는다.
                entry["start"] = entry["start"] or min(first, last)
                entry["end"] = entry["end"] or max(first, last)
        except (OSError, csv.Error):
            pass
        inventory.append(entry)
    return inventory


def resolve_requirement(req_id, inventory, probe_results):
    """요구사항 하나를 available/obtainable/unverified/blocked 로 판정한다."""
    spec = DATA_REQUIREMENTS[req_id]
    kind = spec["kind"]
    detail = {"id": req_id, "label": spec["label"], "kind": kind, "note": spec.get("note")}

    if kind == "local_series":
        found, missing = [], []
        for sym in spec["symbols"]:
            hits = [e for e in inventory if e["symbol"] == sym and e["interval"] == spec["interval"]]
            (found if hits else missing).append(sym)
        detail["found_symbols"] = found
        detail["missing_symbols"] = missing
        detail["status"] = "available" if not missing else "blocked"
        return detail

    if kind == "licensed":
        detail["status"] = "blocked"
        detail["source"] = spec.get("source")
        return detail

    if kind == "unverified":
        detail["status"] = "unverified"
        detail["source"] = spec.get("source")
        return detail

    # free_remote
    detail["source"] = spec.get("source")
    probe = probe_results.get(req_id)
    if probe is None:
        detail["status"] = "obtainable"
        detail["probe"] = "not probed (--probe 미지정)"
    else:
        detail["probe"] = probe
        detail["status"] = "obtainable" if probe.get("ok") else "blocked"
    return detail


def probe_remote(requirements):
    """읽기 전용 원격 접근 확인. --probe 를 준 경우에만 호출된다. 파일을 쓰지 않는다."""
    import urllib.error
    import urllib.request

    results = {}
    headers = {"User-Agent": "trade-guide-research-audit (contact: repository maintainer)"}

    for req_id, spec in requirements.items():
        if spec["kind"] != "free_remote":
            continue
        if "probe_symbols" in spec:
            per_symbol, ok_all = {}, True
            for sym in spec["probe_symbols"]:
                url = ("https://query2.finance.yahoo.com/v8/finance/chart/"
                       f"{sym}?period1=0&period2=9999999999&interval=1wk")
                try:
                    req = urllib.request.Request(url, headers=headers)
                    with urllib.request.urlopen(req, timeout=20) as resp:
                        payload = json.load(resp)
                    result = payload["chart"]["result"][0]
                    per_symbol[sym] = {"ok": True, "bars": len(result["timestamp"])}
                except (urllib.error.URLError, KeyError, IndexError, TypeError, ValueError, OSError) as exc:
                    per_symbol[sym] = {"ok": False, "error": type(exc).__name__}
                    ok_all = False
            results[req_id] = {"ok": ok_all, "symbols": per_symbol}
        elif "probe_url" in spec:
            try:
                req = urllib.request.Request(spec["probe_url"], headers=headers)
                with urllib.request.urlopen(req, timeout=20) as resp:
                    ok = resp.status == 200
                results[req_id] = {"ok": ok, "url": spec["probe_url"]}
            except (urllib.error.URLError, OSError) as exc:
                results[req_id] = {"ok": False, "url": spec["probe_url"], "error": type(exc).__name__}
    return results


def check_backtests_integrity():
    result = {"path": os.path.relpath(BACKTESTS_JSON, REPO_ROOT), "ok": False}
    if not os.path.isfile(BACKTESTS_JSON):
        result["error"] = "파일 없음"
        return result
    try:
        with open(BACKTESTS_JSON, encoding="utf-8") as fh:
            records = json.load(fh)
    except (ValueError, OSError) as exc:
        result["error"] = f"파싱 실패: {type(exc).__name__}"
        return result
    ids = [r.get("id") for r in records]
    missing = [i for i in KNOWN_BACKTEST_IDS if i not in ids]
    added = [i for i in ids if i not in KNOWN_BACKTEST_IDS]
    result.update({
        "record_count": len(records),
        "missing_known_ids": missing,
        "added_ids": added,
        "ok": not missing,
    })
    return result


def check_report_structure():
    result = {"path": os.path.relpath(REPORT_PATH, REPO_ROOT), "ok": False}
    if not os.path.isfile(REPORT_PATH):
        result["error"] = "리포트 파일 없음"
        return result
    with open(REPORT_PATH, encoding="utf-8") as fh:
        text = fh.read()
    missing = [s for s in REQUIRED_REPORT_SECTIONS if s not in text]
    result.update({"missing_sections": missing, "ok": not missing})
    return result


def build_audit(probe=False):
    inventory = scan_cache()
    probe_results = probe_remote(DATA_REQUIREMENTS) if probe else {}

    requirements = {
        req_id: resolve_requirement(req_id, inventory, probe_results)
        for req_id in DATA_REQUIREMENTS
    }

    families = []
    for fam in STRATEGY_FAMILIES:
        reqs = [requirements[r] for r in fam["requires"]]
        worst = max(reqs, key=lambda r: STATUS_ORDER[r["status"]])["status"]
        families.append({
            "id": fam["id"],
            "label": fam["label"],
            "track": fam["track"],
            "selected_as": fam["selected_as"],
            "requirements": fam["requires"],
            "backtestable": worst,
        })

    selected = [f for f in families if f["selected_as"]]
    selected_ok = all(f["backtestable"] in ("available", "obtainable") for f in selected) and len(selected) == 2

    backtests = check_backtests_integrity()
    report = check_report_structure()

    return {
        "generated_by": "research/data/tools/strategy_engine_evidence_audit.py",
        "writes_files": False,
        "probed": probe,
        "cache_inventory": inventory,
        "requirements": requirements,
        "strategy_families": families,
        "selected_candidates_resolved": selected_ok,
        "backtests_json_integrity": backtests,
        "report_structure": report,
        "pass": bool(selected_ok and backtests["ok"] and report["ok"]),
    }


def print_text(audit):
    def line(char="-", n=78):
        print(char * n)

    line("=")
    print("전략 엔진 근거 검토 — 데이터 확보 가능성 감사")
    print("(백테스트를 수행하지 않으며 어떤 파일도 쓰지 않는다)")
    line("=")

    print("\n[1] 로컬 시세 인벤토리  research/data/cache/")
    if not audit["cache_inventory"]:
        print("  (없음)")
    else:
        print(f"  {'symbol':8} {'interval':9} {'rows':>6}  {'start':11} {'end':11} provider")
        for e in audit["cache_inventory"]:
            print(f"  {e['symbol']:8} {e['interval']:9} {str(e['rows'] or '-'):>6}  "
                  f"{str(e['start'] or '-'):11} {str(e['end'] or '-'):11} {e['provider'] or '-'}")

    print("\n[2] 데이터 요구사항 판정")
    for req in audit["requirements"].values():
        print(f"  [{req['status']:10}] {req['label']}")
        if req.get("missing_symbols"):
            print(f"               누락 심볼: {', '.join(req['missing_symbols'])}")
        if req.get("source"):
            print(f"               출처: {req['source']}")
        if req.get("probe") and req["probe"] != "not probed (--probe 미지정)":
            print(f"               probe: {json.dumps(req['probe'], ensure_ascii=False)}")
        if req.get("note"):
            print(f"               비고: {req['note']}")

    print("\n[3] 전략군별 백테스트 가능성")
    print(f"  {'전략군':34} {'트랙':9} {'판정':11} 선택")
    for fam in audit["strategy_families"]:
        mark = fam["selected_as"] or "-"
        print(f"  {fam['label']:34} {fam['track']:9} {fam['backtestable']:11} {mark}")

    print("\n[4] 무결성 검사")
    bt = audit["backtests_json_integrity"]
    print(f"  backtests.json  레코드 {bt.get('record_count', '?')}건 · "
          f"기존 id 보존 {'OK' if bt['ok'] else 'FAIL'}")
    if bt.get("missing_known_ids"):
        print(f"    사라진 기존 id: {', '.join(bt['missing_known_ids'])}")
    if bt.get("added_ids"):
        print(f"    추가된 id: {', '.join(bt['added_ids'])}")
    rp = audit["report_structure"]
    print(f"  리포트 사전 등록 절 구조 {'OK' if rp['ok'] else 'FAIL'}")
    if rp.get("missing_sections"):
        print(f"    누락 절: {rp['missing_sections']}")

    line()
    print(f"선택된 백테스트 후보(2건) 데이터 해결: "
          f"{'YES' if audit['selected_candidates_resolved'] else 'NO'}")
    print(f"전체 판정: {'PASS' if audit['pass'] else 'FAIL'}")
    line()


def main():
    parser = argparse.ArgumentParser(
        description="전략 엔진 근거 검토의 데이터 확보 가능성을 감사한다 (파일을 쓰지 않음).")
    parser.add_argument("--json", action="store_true", help="JSON으로 출력")
    parser.add_argument("--probe", action="store_true",
                        help="무료 공개 소스 접근 가능성을 실제로 조회해 확인 (읽기 전용, 저장 없음)")
    args = parser.parse_args()

    audit = build_audit(probe=args.probe)
    if args.json:
        print(json.dumps(audit, ensure_ascii=False, indent=2))
    else:
        print_text(audit)
    return 0 if audit["pass"] else 1


if __name__ == "__main__":
    sys.exit(main())
