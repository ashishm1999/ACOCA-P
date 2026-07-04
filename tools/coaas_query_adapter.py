#!/usr/bin/env python3
"""CoaaS query-engine adapter.

Wires the CoaaS Query Engine to the four ACOCA-P components in the
order they contribute to the request sequence:

    Consumer --CDQL--> QueryEngine --lookup--> Cache
                            ↓ (on miss or refresh)
                          CAPME  -> PoA
                          CFMS   -> CF + freshness verdict
                          DCMF   -> DST combined belief
                          VACF   -> TTL + replica policy

The adapter runs on the same host as the CoaaS Query Engine and lets
the QE call one blocking function per query rather than chase four
independent services itself.
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import time
from dataclasses import asdict, dataclass
from typing import Any, Optional

import requests


log = logging.getLogger("adapter")


DEFAULT_ENDPOINTS = {
    "capme": os.environ.get("CAPME_URL", "http://capme:8081"),
    "cfms":  os.environ.get("CFMS_URL",  "http://cfms:8082"),
    "dcmf":  os.environ.get("DCMF_URL",  "http://dcmf:8083"),
    "vacf":  os.environ.get("VACF_URL",  "http://vacf:8084"),
}


@dataclass
class Query:
    query_id: str
    item_id: str
    attributes: dict[str, float]
    sla_ms: int = 250

    def to_json(self) -> str:
        return json.dumps(asdict(self))


@dataclass
class Decision:
    item_id: str
    poa: float
    cf: float
    fresh: bool
    cache: bool
    belief: float
    ttl_ms: int
    replicas: int
    total_latency_ms: float


class Adapter:
    def __init__(self, endpoints: dict[str, str] | None = None, timeout_ms: int = 300) -> None:
        self.endpoints = endpoints or DEFAULT_ENDPOINTS
        self.timeout = timeout_ms / 1000.0
        self.session = requests.Session()

    def decide(self, q: Query) -> Decision:
        t0 = time.perf_counter()

        poa = self._poa(q)
        cf, is_fresh = self._cf(q)
        cache, belief = self._dcmf(q.item_id, cf, poa)
        ttl_ms, replicas = self._policy(q.item_id, cf=cf, poa=poa)

        latency_ms = (time.perf_counter() - t0) * 1000.0
        return Decision(
            item_id=q.item_id,
            poa=poa,
            cf=cf,
            fresh=is_fresh,
            cache=cache,
            belief=belief,
            ttl_ms=ttl_ms,
            replicas=replicas,
            total_latency_ms=latency_ms,
        )

    # --- Individual component calls -------------------------------------

    def _poa(self, q: Query) -> float:
        try:
            r = self.session.post(
                f"{self.endpoints['capme']}/api/poa",
                json={"items": {q.item_id: q.attributes}},
                timeout=self.timeout,
            )
            r.raise_for_status()
            return float(r.json().get("scores", {}).get(q.item_id, 0.0))
        except Exception as exc:  # noqa: BLE001
            log.debug("PoA fallback: %s", exc)
            return 0.5

    def _cf(self, q: Query) -> tuple[float, bool]:
        try:
            r = self.session.post(
                f"{self.endpoints['cfms']}/api/freshness/{q.item_id}",
                json={"context_type": "roadwork"},
                timeout=self.timeout,
            )
            if r.status_code == 404:
                return 0.0, False
            r.raise_for_status()
            body = r.json()
            return float(body.get("cf", 0.0)), body.get("verdict") == "retain"
        except Exception as exc:  # noqa: BLE001
            log.debug("CF fallback: %s", exc)
            return 0.0, False

    def _dcmf(self, item_id: str, cf: float, poa: float) -> tuple[bool, float]:
        try:
            r = self.session.post(
                f"{self.endpoints['dcmf']}/api/decide",
                json={"itemId": item_id, "cf": cf, "poa": poa, "quality": 1.0},
                timeout=self.timeout,
            )
            r.raise_for_status()
            body = r.json()
            return bool(body.get("cache", False)), float(body.get("betP", 0.0))
        except Exception as exc:  # noqa: BLE001
            log.debug("DCMF fallback: %s", exc)
            return cf >= 0.5 and poa >= 0.5, 0.5

    def _policy(self, item_id: str, *, cf: float, poa: float) -> tuple[int, int]:
        try:
            r = self.session.post(
                f"{self.endpoints['vacf']}/api/policy",
                json={"itemId": item_id, "cvi": 1 - cf, "cf": cf, "poa": poa, "slaMs": 250},
                timeout=self.timeout,
            )
            r.raise_for_status()
            body = r.json()
            return int(body.get("ttl_ms", 30_000)), int(body.get("replicas", 1))
        except Exception as exc:  # noqa: BLE001
            log.debug("policy fallback: %s", exc)
            return 60_000, 1


def _demo() -> None:
    logging.basicConfig(level=logging.INFO)
    ap = argparse.ArgumentParser(description="CoaaS <-> ACOCA-P bridge demo")
    ap.add_argument("--item-id", default="nerian:frame:nerian-h1:000000042")
    ap.add_argument("--freshness", type=float, default=0.85)
    args = ap.parse_args()

    q = Query(
        query_id="q-demo-1",
        item_id=args.item_id,
        attributes={
            "freshness": args.freshness,
            "popularity": 0.6,
            "recency": 0.9,
            "cost": 1.2,
            "latency": 0.05,
            "quality": 0.95,
        },
    )
    adapter = Adapter()
    d = adapter.decide(q)
    print(json.dumps(asdict(d), indent=2))


if __name__ == "__main__":
    _demo()
