#!/usr/bin/env python3
"""End-to-end benchmark that replays the 280,000-item dataset through the stack.

Each record from `data/roadwork_dataset.jsonl` is submitted to the CoaaS
query-engine adapter, which fans out to CAPME, CFMS, DCMF, and VACF and
returns the composed caching decision. The benchmark reports:

    - throughput (queries/sec)
    - cache hit ratio
    - cache expired ratio
    - median and p95 end-to-end latency
    - per-component call latency

Usage
-----

    python3 benchmark.py --records 5000 --dataset ../data/roadwork_dataset.jsonl
"""

from __future__ import annotations

import argparse
import bisect
import json
import statistics
import time
from pathlib import Path

from coaas_query_adapter import Adapter, Query


def _stats(vals: list[float]) -> dict:
    if not vals:
        return {"n": 0}
    vals_sorted = sorted(vals)
    return {
        "n": len(vals),
        "min": vals_sorted[0],
        "median": statistics.median(vals_sorted),
        "p95": vals_sorted[int(0.95 * (len(vals_sorted) - 1))],
        "p99": vals_sorted[int(0.99 * (len(vals_sorted) - 1))],
        "max": vals_sorted[-1],
        "mean": statistics.fmean(vals_sorted),
    }


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--records", type=int, default=5_000, help="records to replay")
    ap.add_argument("--dataset", type=str, default="../data/roadwork_dataset.jsonl")
    ap.add_argument("--output", type=str, default="benchmark_report.json")
    args = ap.parse_args()

    dataset = Path(args.dataset)
    if not dataset.exists():
        raise SystemExit(f"dataset not found: {dataset}")

    adapter = Adapter()
    latencies: list[float] = []
    hits = 0
    expired = 0
    cache_yes = 0
    cache_no = 0

    started = time.perf_counter()
    with dataset.open("r", encoding="utf-8") as fh:
        for i, line in enumerate(fh):
            if i >= args.records:
                break
            rec = json.loads(line)
            ctx = rec["context"]
            depth = rec["depth_frame"]
            q = Query(
                query_id=f"q-{i:08d}",
                item_id=rec["@id"],
                attributes={
                    "freshness": max(0.0, min(1.0, depth["confidence_ratio"])),
                    "popularity": ctx.get("roadwork.congestion_score", 0.5),
                    "recency": 1.0,
                    "cost": max(0.0, 1.0 - rec["quality"]["provider_reputation"]),
                    "latency": ctx.get("roadwork.sla_ms", 250) / 1000.0,
                    "quality": rec["quality"]["provider_reputation"],
                },
                sla_ms=ctx.get("roadwork.sla_ms", 250),
            )
            d = adapter.decide(q)
            latencies.append(d.total_latency_ms)
            if d.cache:
                cache_yes += 1
                if d.fresh:
                    hits += 1
                else:
                    expired += 1
            else:
                cache_no += 1

    elapsed = time.perf_counter() - started
    total = cache_yes + cache_no
    report = {
        "records_processed": total,
        "elapsed_sec": elapsed,
        "throughput_qps": total / elapsed if elapsed > 0 else 0.0,
        "cache_hit_ratio": hits / total if total else 0.0,
        "cache_expired_ratio": expired / total if total else 0.0,
        "cache_admissions": cache_yes,
        "cache_rejections": cache_no,
        "latency_ms": _stats(latencies),
    }
    Path(args.output).write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
