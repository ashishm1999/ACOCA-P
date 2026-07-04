"""Runtime configuration for CAPME.

All knobs are sourced from environment variables so the same image runs
across the Melbourne testbed nodes and the CI test bench with different
settings.
"""

import os


def _f(name: str, default: float) -> float:
    return float(os.environ.get(name, default))


def _i(name: str, default: int) -> int:
    return int(os.environ.get(name, default))


class Config:
    # --- Server ---
    PORT: int = _i("CAPME_PORT", 8081)

    # --- DQN hyperparameters (Chapter 5 of the thesis) ---
    LEARNING_RATE: float = _f("CAPME_LEARNING_RATE", 0.001)
    DISCOUNT_FACTOR: float = _f("CAPME_DISCOUNT_FACTOR", 0.95)
    EPSILON_START: float = _f("CAPME_EPSILON_START", 1.0)
    EPSILON_MIN: float = _f("CAPME_EPSILON_MIN", 0.01)
    EPSILON_DECAY: float = _f("CAPME_EPSILON_DECAY", 0.995)
    BATCH_SIZE: int = _i("CAPME_BATCH_SIZE", 32)
    REPLAY_CAPACITY: int = _i("CAPME_REPLAY_CAPACITY", 10_000)
    TARGET_SYNC_STEPS: int = _i("CAPME_TARGET_SYNC_STEPS", 100)

    # --- MAUT ---
    # Six-attribute utility hierarchy: freshness, popularity, recency,
    # cost, latency, quality.
    ATTRIBUTES = ("freshness", "popularity", "recency", "cost", "latency", "quality")

    # --- Storage ---
    MONGO_URI: str = os.environ.get("MONGO_URI", "mongodb://mongo:27017/acoca")

    # --- Broker (for publishing refined weights to CFMS + DCMF) ---
    BROKER_TOPIC: str = os.environ.get("CAPME_TOPIC", "acoca.capme.weights")
