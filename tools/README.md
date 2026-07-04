# tools/

Command-line utilities that sit alongside the ACOCA-P microservices.

| Tool                        | Purpose                                                                        |
|-----------------------------|--------------------------------------------------------------------------------|
| `coaas_query_adapter.py`    | Blocking Python adapter that composes CAPME → CFMS → DCMF → VACF per query.    |
| `benchmark.py`              | Replays the 280,000-record roadwork dataset through the adapter and reports throughput, CHR, CER, p50/p95/p99 latency. |

## Running

Both tools speak the four ACOCA-P HTTP APIs. Point them at the stack
that `docker compose up` brings online:

```bash
python3 coaas_query_adapter.py --item-id nerian:frame:nerian-h1:000000042 --freshness 0.85

python3 benchmark.py --records 5000 --dataset ../data/roadwork_dataset.jsonl
```

The adapter degrades gracefully when a component is unavailable (each
per-component call has a small timeout and returns a documented fallback
value), so the benchmark can be run against a partial deployment during
development.
