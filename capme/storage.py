"""Persistence layer for CAPME.

CAPME persists the DQN's refined weights and a rolling snapshot of the
replay buffer so that a container restart resumes training rather than
re-warming from scratch. MongoDB is the default (aligned with the
CoaaS deployment); the in-memory backend is a testing fallback.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from typing import Any, Iterable

try:
    from pymongo import MongoClient
    from pymongo.collection import Collection
    _MONGO_AVAILABLE = True
except ImportError:
    _MONGO_AVAILABLE = False


log = logging.getLogger("capme.storage")


@dataclass
class WeightSnapshot:
    attributes: list[str]
    weights: list[float]
    saved_at: float
    step: int
    epsilon: float


class MemoryStorage:
    """Trivial in-memory storage — used by tests."""

    def __init__(self) -> None:
        self._snap: WeightSnapshot | None = None
        self._replay: list[dict] = []

    def save_weights(self, snap: WeightSnapshot) -> None:
        self._snap = snap

    def load_weights(self) -> WeightSnapshot | None:
        return self._snap

    def append_transition(self, transition: dict) -> None:
        self._replay.append(transition)
        # Keep the buffer bounded to match the training-time cap.
        if len(self._replay) > 10_000:
            self._replay = self._replay[-10_000:]

    def load_transitions(self, n: int) -> Iterable[dict]:
        return list(self._replay[-n:])


class MongoStorage:
    """MongoDB-backed storage. Collections are lazily created."""

    def __init__(self, uri: str, db_name: str = "acoca") -> None:
        if not _MONGO_AVAILABLE:
            raise RuntimeError("pymongo not installed")
        self._client = MongoClient(uri, serverSelectionTimeoutMS=1500)
        self._db = self._client[db_name]
        self._weights: Collection = self._db["capme_weights"]
        self._replay: Collection = self._db["capme_replay"]
        # Cap the replay collection so a restart doesn't try to load millions.
        try:
            self._db.create_collection(
                "capme_replay_capped",
                capped=True,
                size=64 * 1024 * 1024,
                max=10_000,
            )
        except Exception:
            pass

    def save_weights(self, snap: WeightSnapshot) -> None:
        doc: dict[str, Any] = {
            "attributes": snap.attributes,
            "weights": snap.weights,
            "saved_at": snap.saved_at,
            "step": snap.step,
            "epsilon": snap.epsilon,
        }
        self._weights.insert_one(doc)

    def load_weights(self) -> WeightSnapshot | None:
        doc = self._weights.find_one(sort=[("saved_at", -1)])
        if doc is None:
            return None
        return WeightSnapshot(
            attributes=list(doc["attributes"]),
            weights=list(doc["weights"]),
            saved_at=float(doc["saved_at"]),
            step=int(doc.get("step", 0)),
            epsilon=float(doc.get("epsilon", 1.0)),
        )

    def append_transition(self, transition: dict) -> None:
        transition = {**transition, "captured_at": time.time()}
        self._replay.insert_one(transition)

    def load_transitions(self, n: int) -> Iterable[dict]:
        cur = self._replay.find().sort("captured_at", -1).limit(n)
        return list(cur)


def build_storage(uri: str | None) -> MemoryStorage | MongoStorage:
    if not uri or not _MONGO_AVAILABLE:
        return MemoryStorage()
    try:
        return MongoStorage(uri)
    except Exception as exc:  # noqa: BLE001
        log.warning("MongoDB unavailable at %s (%s) — using in-memory storage", uri, exc)
        return MemoryStorage()
