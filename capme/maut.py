"""Multi-Attribute Utility Theory scorer.

MAUT combines several utility dimensions into a single scalar under an
assumption of preferential independence. In ACOCA-P we use it to compute
the Probability of Access (PoA) for a context item from six attributes:

    freshness  — how recently the item was updated
    popularity — normalised access count in the sliding window
    recency    — normalised time since last access
    cost       — provider retrieval cost, inverted
    latency    — end-to-end latency to the provider, inverted
    quality    — CoaaS-reported Quality of Context

The vector of attribute weights is refined online by the DQN in `dqn.py`.
This module holds the pure MAUT arithmetic and the utility functions —
DQN sits above it and adjusts the weights.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping, Sequence

import numpy as np

from config import Config


@dataclass(frozen=True)
class Attributes:
    """Raw attribute values for one context item."""

    freshness: float
    popularity: float
    recency: float
    cost: float
    latency: float
    quality: float

    def to_vector(self) -> np.ndarray:
        return np.array(
            [
                self.freshness,
                self.popularity,
                self.recency,
                self.cost,
                self.latency,
                self.quality,
            ],
            dtype=np.float32,
        )


# --- Utility functions ----------------------------------------------------

def _identity(x: float) -> float:
    """Utility for values that are already in [0, 1] and higher-is-better."""
    return float(np.clip(x, 0.0, 1.0))


def _inverse(x: float) -> float:
    """Utility for values where lower-is-better, folded into [0, 1]."""
    return float(1.0 / (1.0 + max(x, 0.0)))


_UTILITY = {
    "freshness": _identity,
    "popularity": _identity,
    "recency": _identity,
    "cost": _inverse,
    "latency": _inverse,
    "quality": _identity,
}


def utility_vector(attrs: Attributes) -> np.ndarray:
    """Apply the per-attribute utility function to each raw value."""
    raw = {
        "freshness": attrs.freshness,
        "popularity": attrs.popularity,
        "recency": attrs.recency,
        "cost": attrs.cost,
        "latency": attrs.latency,
        "quality": attrs.quality,
    }
    return np.array(
        [_UTILITY[name](raw[name]) for name in Config.ATTRIBUTES],
        dtype=np.float32,
    )


# --- MAUT scoring ---------------------------------------------------------

def normalise_weights(weights: Sequence[float]) -> np.ndarray:
    """Project any weight vector onto the probability simplex."""
    w = np.array(weights, dtype=np.float32)
    w = np.maximum(w, 0.0)
    s = w.sum()
    if s == 0:
        return np.full(len(w), 1.0 / len(w), dtype=np.float32)
    return w / s


def poa(attrs: Attributes, weights: Sequence[float]) -> float:
    """Compute the Probability of Access for a context item.

    PoA = sum_i w_i * u_i(a_i), with w on the simplex and u on [0, 1].
    Result is guaranteed to be in [0, 1].
    """
    w = normalise_weights(weights)
    u = utility_vector(attrs)
    return float(np.dot(w, u))


def batch_poa(
    items: Mapping[str, Attributes],
    weights: Sequence[float],
) -> dict[str, float]:
    """Vectorised PoA computation for many items sharing the same weights."""
    w = normalise_weights(weights)
    return {item_id: float(np.dot(w, utility_vector(a))) for item_id, a in items.items()}


# --- Baseline weights -----------------------------------------------------

def uniform_weights() -> np.ndarray:
    """Uniform prior over the six attributes, used before DQN warm-up."""
    n = len(Config.ATTRIBUTES)
    return np.full(n, 1.0 / n, dtype=np.float32)
