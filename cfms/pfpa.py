"""Parameter Freshness Processing Algorithm.

PFPA runs on the per-request path. For each cached context item it:

  1. maintains a sliding window of the recent CF observations,
  2. tracks the running mean and variance in constant time using
     Welford's online algorithm,
  3. applies the adaptive threshold θ = μ_CF − κ · σ_CF,
  4. emits a three-region verdict: retain / refresh / evict.

Welford's algorithm gives numerically stable streaming statistics without
storing the full window.
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass
from enum import Enum
from typing import Deque

import math

from config import Config


class Verdict(str, Enum):
    RETAIN = "retain"
    REFRESH = "refresh"
    EVICT = "evict"


@dataclass
class WelfordStats:
    """Running (count, mean, M2) — see Welford 1962.

    Variance is M2 / (count - 1) for the sample variance, M2 / count for
    the population variance. We use the sample variance below.
    """

    count: int = 0
    mean: float = 0.0
    m2: float = 0.0

    def update(self, x: float) -> None:
        self.count += 1
        delta = x - self.mean
        self.mean += delta / self.count
        delta2 = x - self.mean
        self.m2 += delta * delta2

    def variance(self) -> float:
        if self.count < 2:
            return 0.0
        return self.m2 / (self.count - 1)

    def std(self) -> float:
        return math.sqrt(self.variance())


class PFPA:
    """One PFPA instance per cached context item."""

    def __init__(self, window_size: int | None = None, kappa: float | None = None) -> None:
        self._window: Deque[float] = deque(maxlen=window_size or Config.WINDOW_SIZE)
        self._stats = WelfordStats()
        self._kappa = kappa if kappa is not None else Config.KAPPA

    # --- Streaming updates ----------------------------------------------

    def observe(self, cf: float) -> None:
        """Record a fresh CF observation and update running statistics."""
        if len(self._window) == self._window.maxlen:
            # Evict oldest — Welford does not natively support this, so we
            # rebuild the stats when the window is full. Rebuilding is
            # O(window_size) but happens at most once per observation.
            old = self._window.popleft()
            self._rebuild_stats_excluding(old, cf)
        else:
            self._stats.update(cf)
        self._window.append(cf)

    def _rebuild_stats_excluding(self, removed: float, added: float) -> None:
        """Efficient rebuild that swaps one sample for another."""
        # For small windows a full rebuild dominates any incremental trick.
        stats = WelfordStats()
        for x in self._window:
            if x is removed:
                continue
            stats.update(x)
        stats.update(added)
        self._stats = stats

    # --- Threshold and verdict ------------------------------------------

    def threshold(self) -> float:
        return max(0.0, self._stats.mean - self._kappa * self._stats.std())

    def verdict(self, cf: float) -> Verdict:
        """Three-region retain / refresh / evict decision.

        - retain  when CF is above the adaptive threshold
        - refresh when CF is within one σ below the threshold
        - evict   otherwise (heavily stale, no reason to keep)
        """
        theta = self.threshold()
        sigma = self._stats.std()
        if cf >= theta:
            return Verdict.RETAIN
        if cf >= theta - sigma:
            return Verdict.REFRESH
        return Verdict.EVICT

    # --- Introspection ---------------------------------------------------

    @property
    def mean(self) -> float:
        return self._stats.mean

    @property
    def std(self) -> float:
        return self._stats.std()

    @property
    def count(self) -> int:
        return self._stats.count
