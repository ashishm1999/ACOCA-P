"""Weight-publication broker.

CAPME publishes its refined MAUT weights to the CoaaS broker so that
CFMS, DCMF, and VACF can consume the current PoA weighting without
polling CAPME on every query. The transport is Redis pub/sub in the
default deployment; the interface is small enough to swap for MQTT or
Kafka with a single method override.
"""

from __future__ import annotations

import json
import logging
import os
from typing import Iterable

try:
    import redis
    _REDIS_AVAILABLE = True
except ImportError:
    _REDIS_AVAILABLE = False


log = logging.getLogger("capme.broker")


class Broker:
    """Thin publisher over Redis pub/sub with a graceful no-op fallback."""

    def __init__(self, topic: str, url: str | None = None) -> None:
        self.topic = topic
        self._url = url or os.environ.get("REDIS_URL", "redis://redis:6379/0")
        self._client = None
        if _REDIS_AVAILABLE:
            try:
                self._client = redis.Redis.from_url(self._url, socket_connect_timeout=1.0)
                self._client.ping()
            except Exception as exc:  # noqa: BLE001
                log.warning("Redis unavailable at %s (%s) — publishes will no-op", self._url, exc)
                self._client = None
        else:
            log.warning("redis-py not installed — publishes will no-op")

    def publish_weights(self, attributes: Iterable[str], weights: Iterable[float]) -> bool:
        """Publish a fresh weight vector. Returns True on delivery."""
        payload = {
            "attributes": list(attributes),
            "weights": [float(w) for w in weights],
        }
        return self._publish(payload)

    def publish_action(self, action_idx: int, epsilon: float) -> bool:
        payload = {"kind": "action", "action_idx": int(action_idx), "epsilon": float(epsilon)}
        return self._publish(payload)

    def _publish(self, payload: dict) -> bool:
        if self._client is None:
            return False
        try:
            self._client.publish(self.topic, json.dumps(payload).encode("utf-8"))
            return True
        except Exception as exc:  # noqa: BLE001
            log.warning("publish failed: %s", exc)
            return False


class NullBroker(Broker):
    """Explicit no-op used in tests and in the CI test bench."""

    def __init__(self) -> None:  # noqa: D401
        # Bypass Broker.__init__ so we don't try to connect to Redis.
        self.topic = "null"
        self._url = ""
        self._client = None
