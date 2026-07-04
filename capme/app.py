"""CAPME service — Flask entry point.

Exposes:

    GET  /health                     — liveness probe
    POST /api/poa                    — compute PoA for a batch of items
    GET  /api/weights                — read the current MAUT weight vector
    POST /api/reward                 — feed a training reward to the DQN
    GET  /api/config                 — dump active runtime config
"""

from __future__ import annotations

import logging
import numpy as np
from flask import Flask, jsonify, request

from config import Config
from dqn import ACTION_SPACE, DQNAgent
from maut import Attributes, batch_poa, uniform_weights


logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("capme")

app = Flask(__name__)
_agent = DQNAgent(uniform_weights())
_last_state: np.ndarray | None = None
_last_action: int | None = None


@app.get("/health")
def health():
    return jsonify({"status": "ok", "component": "CAPME"})


@app.get("/api/weights")
def get_weights():
    return jsonify(
        {
            "attributes": list(Config.ATTRIBUTES),
            "weights": _agent.weights.tolist(),
            "epsilon": _agent.epsilon,
            "step": _agent.step,
        }
    )


@app.post("/api/poa")
def compute_poa():
    """Body: {"items": {"id1": {freshness, popularity, ...}, ...}}"""
    global _last_state, _last_action
    body = request.get_json(force=True) or {}
    raw_items = body.get("items", {})
    items = {}
    for item_id, attrs in raw_items.items():
        try:
            items[item_id] = Attributes(
                freshness=float(attrs.get("freshness", 0.0)),
                popularity=float(attrs.get("popularity", 0.0)),
                recency=float(attrs.get("recency", 0.0)),
                cost=float(attrs.get("cost", 0.0)),
                latency=float(attrs.get("latency", 0.0)),
                quality=float(attrs.get("quality", 1.0)),
            )
        except (TypeError, ValueError) as exc:
            return jsonify({"error": f"bad attributes for {item_id}: {exc}"}), 400

    scores = batch_poa(items, _agent.weights)

    # Optionally advance the DQN using the aggregate cache state that the
    # caller passes in — this is what closes the reward loop.
    state_arr = body.get("state")
    if state_arr is not None:
        state = np.array(state_arr, dtype=np.float32)
        if state.shape == (6,):
            action = _agent.select_action(state)
            _agent.apply_action(action)
            _last_state = state
            _last_action = action

    return jsonify({"scores": scores, "epsilon": _agent.epsilon})


@app.post("/api/reward")
def observe_reward():
    """Body: {"reward": <float>, "next_state": [...], "done": bool}"""
    global _last_state, _last_action
    if _last_state is None or _last_action is None:
        return jsonify({"error": "no pending action to reward"}), 409
    body = request.get_json(force=True) or {}
    try:
        reward = float(body["reward"])
        next_state = np.array(body["next_state"], dtype=np.float32)
        done = bool(body.get("done", False))
    except (KeyError, TypeError, ValueError) as exc:
        return jsonify({"error": f"bad reward payload: {exc}"}), 400

    _agent.observe((_last_state, _last_action, reward, next_state, done))
    loss = _agent.train()
    _last_state = None
    _last_action = None
    return jsonify({"loss": loss, "step": _agent.step, "epsilon": _agent.epsilon})


@app.get("/api/config")
def dump_config():
    return jsonify(
        {
            "learning_rate": Config.LEARNING_RATE,
            "discount_factor": Config.DISCOUNT_FACTOR,
            "epsilon_start": Config.EPSILON_START,
            "epsilon_min": Config.EPSILON_MIN,
            "epsilon_decay": Config.EPSILON_DECAY,
            "batch_size": Config.BATCH_SIZE,
            "replay_capacity": Config.REPLAY_CAPACITY,
            "target_sync_steps": Config.TARGET_SYNC_STEPS,
            "action_space": [(a, s) for a, s in ACTION_SPACE],
        }
    )


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=Config.PORT, debug=False)
