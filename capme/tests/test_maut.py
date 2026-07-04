"""Unit tests for the MAUT scorer."""

import numpy as np
import pytest

from maut import Attributes, normalise_weights, poa, uniform_weights


def _attrs(**kwargs):
    defaults = dict(
        freshness=0.8,
        popularity=0.6,
        recency=0.9,
        cost=1.2,
        latency=0.05,
        quality=0.95,
    )
    defaults.update(kwargs)
    return Attributes(**defaults)


def test_uniform_weights_sum_to_one():
    w = uniform_weights()
    assert abs(w.sum() - 1.0) < 1e-6


def test_normalise_weights_projects_to_simplex():
    w = normalise_weights([1, 2, 3, 4, 5, 6])
    assert abs(w.sum() - 1.0) < 1e-6
    assert (w >= 0).all()


def test_normalise_weights_handles_zero_vector():
    w = normalise_weights([0, 0, 0, 0, 0, 0])
    assert abs(w.sum() - 1.0) < 1e-6
    # All entries should be equal.
    assert np.allclose(w, w[0])


def test_normalise_weights_clips_negatives():
    w = normalise_weights([1, -1, 0, 1, 0, 1])
    assert (w >= 0).all()
    assert abs(w.sum() - 1.0) < 1e-6


def test_poa_is_bounded():
    for _ in range(20):
        a = _attrs(
            freshness=float(np.random.rand()),
            popularity=float(np.random.rand()),
            recency=float(np.random.rand()),
            cost=float(np.random.rand() * 5),
            latency=float(np.random.rand()),
            quality=float(np.random.rand()),
        )
        score = poa(a, uniform_weights())
        assert 0.0 <= score <= 1.0


def test_poa_prefers_fresh_items():
    fresh = _attrs(freshness=0.95, popularity=0.5, recency=0.5)
    stale = _attrs(freshness=0.10, popularity=0.5, recency=0.5)
    # Weight freshness heavily and confirm the fresh item wins.
    weights = [1.0, 0.0, 0.0, 0.0, 0.0, 0.0]
    assert poa(fresh, weights) > poa(stale, weights)


def test_poa_penalises_high_cost():
    cheap = _attrs(cost=0.0)
    expensive = _attrs(cost=10.0)
    weights = [0.0, 0.0, 0.0, 1.0, 0.0, 0.0]  # only cost matters
    assert poa(cheap, weights) > poa(expensive, weights)
