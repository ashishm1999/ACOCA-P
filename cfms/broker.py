"""Weight-subscription broker for CFMS.

CFMS subscribes to CAPME's weight-refinement channel so that the DSA
weights can be blended with the runtime PoA signal on the per-request
path. The subscriber runs on a background thread that fills a queue;
the main freshness-check path reads from the queue without blocking.
"""

from __future__ import annotations

import json
import logging
import os
import queue
import threading
from typing import Callable, Optional

try:
    import redis
    _REDIS_AVAILABLE = True
except ImportError:
    _REDIS_AVAILABLE = False


log = logging.getLogger("cfms.broker")


class WeightSubscriber:
    """Background subscriber that pushes decoded payloads into a queue."""

    def __init__(self, topic: str, url: str | None = None, maxsize: int = 100) -> None:
        self.topic = topic
        self._url = url or os.environ.get("REDIS_URL", "redis://redis:6379/0")
        self._q: queue.Queue[dict] = queue.Queue(maxsize=maxsize)
        self._thread: Optional[threading.Thread] = None
        self._stop = threading.Event()
        self._client = None
        self._on_message: Callable[[dict], None] | None = None

    def start(self, on_message: Callable[[dict], None] | None = None) -> None:
        self._on_message = on_message
        if not _REDIS_AVAILABLE:
            log.warning("redis-py not installed — subscription disabled")
            return
        try:
            self._client = redis.Redis.from_url(self._url, socket_connect_timeout=1.0)
            self._client.ping()
        except Exception as exc:  # noqa: BLE001
            log.warning("Redis unavailable at %s (%s) — subscription disabled", self._url, exc)
            self._client = None
            return
        self._thread = threading.Thread(target=self._loop, daemon=True, name="cfms-sub")
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()

    def _loop(self) -> None:
        pubsub = self._client.pubsub()
        pubsub.subscribe(self.topic)
        for msg in pubsub.listen():
            if self._stop.is_set():
                break
            if msg.get("type") != "message":
                continue
            try:
                payload = json.loads(msg["data"].decode("utf-8"))
            except Exception:  # noqa: BLE001
                continue
            try:
                self._q.put_nowait(payload)
            except queue.Full:
                self._q.get_nowait()
                self._q.put_nowait(payload)
            if self._on_message is not None:
                try:
                    self._on_message(payload)
                except Exception as exc:  # noqa: BLE001
                    log.warning("on_message raised: %s", exc)

    def latest(self) -> dict | None:
        try:
            return self._q.get_nowait()
        except queue.Empty:
            return None
