"""Decision Supporting Algorithm — Fuzzy Analytic Hierarchy Process.

The DSA derives an importance weight for each context attribute the
application depends on. It uses the Fuzzy AHP variant of Saaty's AHP
(Saaty 1987; Chang 1996 extent analysis) so that pairwise judgements
carry uncertainty rather than being pinned to a single scalar.

Fuzzy AHP works in three stages:

1. Build a pairwise comparison matrix of triangular fuzzy numbers
   (l, m, u) from domain-expert judgements.
2. Aggregate each row by the geometric mean of its fuzzy elements.
3. Defuzzify (centroid) and normalise to a weight vector on the simplex.

The eigenvector step is off the per-request path — weights are computed
once per context type and updated only when priorities change.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

import numpy as np


@dataclass(frozen=True)
class TFN:
    """Triangular fuzzy number (l, m, u) with l <= m <= u."""

    l: float
    m: float
    u: float

    def centroid(self) -> float:
        """Defuzzify by centroid: (l + m + u) / 3."""
        return (self.l + self.m + self.u) / 3.0


def geometric_mean(row: Sequence[TFN]) -> TFN:
    """Geometric mean of a row of triangular fuzzy numbers."""
    n = len(row)
    if n == 0:
        return TFN(1.0, 1.0, 1.0)
    ls = np.prod([r.l for r in row]) ** (1.0 / n)
    ms = np.prod([r.m for r in row]) ** (1.0 / n)
    us = np.prod([r.u for r in row]) ** (1.0 / n)
    return TFN(float(ls), float(ms), float(us))


def fuzzy_ahp_weights(matrix: Sequence[Sequence[TFN]]) -> np.ndarray:
    """Derive attribute weights from a fuzzy pairwise comparison matrix.

    Parameters
    ----------
    matrix
        Square (n × n) matrix of TFNs. matrix[i][j] is the fuzzy judgement
        of attribute i relative to attribute j.

    Returns
    -------
    weights
        1D array of length n on the probability simplex.
    """
    n = len(matrix)
    if n == 0 or any(len(row) != n for row in matrix):
        raise ValueError("fuzzy AHP matrix must be square")

    row_means = np.array(
        [geometric_mean(row).centroid() for row in matrix],
        dtype=np.float32,
    )
    total = row_means.sum()
    if total == 0:
        return np.full(n, 1.0 / n, dtype=np.float32)
    return row_means / total


def consistency_ratio(matrix: Sequence[Sequence[TFN]]) -> float:
    """Saaty's consistency ratio on the defuzzified crisp matrix.

    A CR below 0.10 is conventionally treated as acceptable.
    """
    crisp = np.array(
        [[cell.centroid() for cell in row] for row in matrix],
        dtype=np.float32,
    )
    n = crisp.shape[0]
    if n < 3:
        return 0.0
    eigvals = np.linalg.eigvals(crisp)
    lam_max = float(np.max(eigvals.real))
    ci = (lam_max - n) / (n - 1)
    # Random consistency index (Saaty 1987)
    ri_table = [0.0, 0.0, 0.58, 0.9, 1.12, 1.24, 1.32, 1.41, 1.45, 1.49]
    ri = ri_table[min(n - 1, len(ri_table) - 1)]
    if ri == 0.0:
        return 0.0
    return ci / ri
