"""Context Freshness — the exponential decay model.

Chapter 6 of the thesis defines Context Freshness for a cached item as

    CF(t) = exp(-λ · (t - t_last))

with λ tuned per attribute to match its observed decay rate. This module
holds the arithmetic; the higher-level services in `app.py` and `pfpa.py`
compose it with per-attribute weights and a sliding-window statistical
threshold.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Iterable, Mapping, Sequence

import numpy as np


@dataclass(frozen=True)
class AttributeSample:
    """Observed value for one context attribute at a point in time."""

    name: str
    value: float
    t_last: float  # unix seconds


def cf(t_last: float, now: float, decay: float) -> float:
    """Compute CF(t) = exp(-λ · (t - t_last)), clipped to [0, 1]."""
    dt = max(now - t_last, 0.0)
    return math.exp(-decay * dt)


def weighted_cf(
    samples: Iterable[AttributeSample],
    weights: Mapping[str, float],
    now: float,
    decay: float,
) -> float:
    """Weighted freshness across the attributes of one cached item.

    Weights are expected to come from the DSA (Fuzzy AHP) and to sum to 1.
    """
    total_w = 0.0
    total = 0.0
    for s in samples:
        w = float(weights.get(s.name, 0.0))
        if w <= 0.0:
            continue
        total += w * cf(s.t_last, now, decay)
        total_w += w
    if total_w == 0.0:
        return 0.0
    return total / total_w


def adaptive_threshold(mu: float, sigma: float, kappa: float) -> float:
    """θ = μ_CF − κ · σ_CF."""
    return max(0.0, mu - kappa * sigma)


def verdict(cf_value: float, mu: float, sigma: float, kappa: float) -> tuple[float, bool]:
    """Return (cf_value, is_fresh) applying the adaptive threshold."""
    theta = adaptive_threshold(mu, sigma, kappa)
    return cf_value, cf_value >= theta
