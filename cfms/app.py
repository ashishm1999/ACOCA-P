"""CFMS service — Flask entry point.

Endpoints (Chapter 6 of the thesis):

    GET  /health                      — liveness probe
    GET  /api/freshness/<ci_id>       — CF + verdict for one cached item
    POST /api/observe                 — feed a fresh CF value into PFPA
    POST /api/weights/<ctype>         — post per-attribute DSA weights
    GET  /api/weights/<ctype>         — read DSA weights for a context type
    GET  /api/config
"""

from __future__ import annotations

import logging
import time
from threading import Lock
from typing import Dict

from flask import Flask, jsonify, request

from config import Config
from dsa import TFN, consistency_ratio, fuzzy_ahp_weights
from freshness import AttributeSample, weighted_cf
from pfpa import PFPA


logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("cfms")

app = Flask(__name__)

# Per-item PFPA monitors — created lazily.
_monitors: Dict[str, PFPA] = {}
_monitors_lock = Lock()

# Per-context-type DSA weights.
_weights: Dict[str, Dict[str, float]] = {}


def _get_monitor(ci_id: str) -> PFPA:
    with _monitors_lock:
        m = _monitors.get(ci_id)
        if m is None:
            m = PFPA()
            _monitors[ci_id] = m
        return m


@app.get("/health")
def health():
    return jsonify({"status": "ok", "component": "CFMS"})


@app.get("/api/freshness/<ci_id>")
def check_freshness(ci_id: str):
    monitor = _monitors.get(ci_id)
    if monitor is None or monitor.count == 0:
        return jsonify({"status": "miss"}), 404
    # Callers may pass the most recent samples so we score with the actual
    # value; otherwise we score with the running mean.
    body = request.get_json(silent=True) or {}
    samples = body.get("samples")
    ctype = body.get("context_type", "default")
    weights = _weights.get(ctype, {})
    now = float(body.get("now", time.time()))
    decay = float(body.get("decay", Config.LAMBDA_DECAY))

    if samples:
        parsed = [
            AttributeSample(
                name=s["name"],
                value=float(s.get("value", 0.0)),
                t_last=float(s.get("t_last", now)),
            )
            for s in samples
        ]
        cf_value = weighted_cf(parsed, weights, now, decay)
    else:
        cf_value = monitor.mean

    monitor.observe(cf_value)
    verdict = monitor.verdict(cf_value)
    return jsonify(
        {
            "ci_id": ci_id,
            "cf": cf_value,
            "verdict": verdict.value,
            "threshold": monitor.threshold(),
            "mu": monitor.mean,
            "sigma": monitor.std,
            "kappa": Config.KAPPA,
        }
    )


@app.post("/api/observe")
def observe_cf():
    """Feed a fresh CF value into the item's PFPA state."""
    body = request.get_json(force=True) or {}
    try:
        ci_id = body["ci_id"]
        cf_value = float(body["cf"])
    except (KeyError, ValueError, TypeError) as exc:
        return jsonify({"error": f"bad payload: {exc}"}), 400
    monitor = _get_monitor(ci_id)
    monitor.observe(cf_value)
    return jsonify({"ci_id": ci_id, "mu": monitor.mean, "sigma": monitor.std, "count": monitor.count})


@app.post("/api/weights/<ctype>")
def post_weights(ctype: str):
    """Compute DSA weights from a Fuzzy AHP matrix and cache them.

    Body:
      {
        "attributes": ["a", "b", "c"],
        "matrix": [[[l,m,u], ...], ...]     # n×n TFNs
      }
    """
    body = request.get_json(force=True) or {}
    try:
        attrs = list(body["attributes"])
        matrix = [[TFN(*cell) for cell in row] for row in body["matrix"]]
    except (KeyError, TypeError, ValueError) as exc:
        return jsonify({"error": f"bad matrix: {exc}"}), 400
    if len(matrix) != len(attrs):
        return jsonify({"error": "matrix size disagrees with attributes"}), 400
    cr = consistency_ratio(matrix)
    if cr > 0.10:
        return jsonify({"error": f"consistency ratio {cr:.3f} > 0.10"}), 400
    w = fuzzy_ahp_weights(matrix)
    _weights[ctype] = {name: float(v) for name, v in zip(attrs, w)}
    return jsonify({"context_type": ctype, "weights": _weights[ctype], "consistency_ratio": cr})


@app.get("/api/weights/<ctype>")
def get_weights(ctype: str):
    w = _weights.get(ctype)
    if w is None:
        return jsonify({"error": "unknown context type"}), 404
    return jsonify({"context_type": ctype, "weights": w})


@app.get("/api/config")
def dump_config():
    return jsonify(
        {
            "kappa": Config.KAPPA,
            "window_size": Config.WINDOW_SIZE,
            "lambda_decay": Config.LAMBDA_DECAY,
            "attributes": list(Config.ATTRIBUTES),
        }
    )


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=Config.PORT, debug=False)
