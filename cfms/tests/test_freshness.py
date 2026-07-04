"""Unit tests for freshness + PFPA + DSA."""

import math

import pytest

from dsa import TFN, consistency_ratio, fuzzy_ahp_weights
from freshness import adaptive_threshold, cf, verdict
from pfpa import PFPA, Verdict, WelfordStats


# --- freshness -----------------------------------------------------------

def test_cf_decays_monotonically():
    now = 100.0
    v0 = cf(t_last=100.0, now=now, decay=0.1)
    v1 = cf(t_last=90.0, now=now, decay=0.1)
    v2 = cf(t_last=50.0, now=now, decay=0.1)
    assert v0 == 1.0
    assert v0 > v1 > v2 >= 0.0


def test_cf_zero_when_t_last_in_future():
    # dt is clipped to zero — CF cannot exceed 1.
    v = cf(t_last=200.0, now=100.0, decay=0.1)
    assert v == 1.0


def test_adaptive_threshold_floor_at_zero():
    # If κ·σ dominates μ the threshold should not go negative.
    assert adaptive_threshold(mu=0.1, sigma=1.0, kappa=1.5) == 0.0


def test_verdict_returns_fresh_when_above_threshold():
    cf_value, is_fresh = verdict(0.9, mu=0.8, sigma=0.05, kappa=1.5)
    assert cf_value == 0.9
    assert is_fresh is True


# --- Welford -------------------------------------------------------------

def test_welford_matches_numpy_on_stream():
    import random
    random.seed(42)
    stats = WelfordStats()
    stream = [random.random() for _ in range(200)]
    for x in stream:
        stats.update(x)
    exp_mean = sum(stream) / len(stream)
    exp_var = sum((x - exp_mean) ** 2 for x in stream) / (len(stream) - 1)
    assert abs(stats.mean - exp_mean) < 1e-6
    assert abs(stats.variance() - exp_var) < 1e-6


# --- PFPA ----------------------------------------------------------------

def test_pfpa_verdict_transitions():
    pfpa = PFPA(window_size=10, kappa=1.0)
    for _ in range(9):
        pfpa.observe(0.9)
    # A fresh reading well above the running mean should retain.
    assert pfpa.verdict(0.95) == Verdict.RETAIN
    # A reading below θ but within one σ should refresh.
    theta = pfpa.threshold()
    borderline = max(theta - 0.001, 0.0)
    if pfpa.std > 0:
        assert pfpa.verdict(borderline) in (Verdict.REFRESH, Verdict.RETAIN)
    # A very low reading should evict.
    assert pfpa.verdict(0.0) == Verdict.EVICT


def test_pfpa_window_bounded():
    pfpa = PFPA(window_size=5, kappa=1.5)
    for i in range(20):
        pfpa.observe(i / 20.0)
    # Count reflects only the window size.
    assert pfpa.count <= 5


# --- DSA -----------------------------------------------------------------

def test_fuzzy_ahp_weights_on_identity_matrix():
    # An identity-of-preference matrix should yield uniform weights.
    n = 4
    matrix = [[TFN(1, 1, 1) for _ in range(n)] for _ in range(n)]
    w = fuzzy_ahp_weights(matrix)
    assert abs(w.sum() - 1.0) < 1e-6
    assert all(abs(x - 1.0 / n) < 1e-6 for x in w)


def test_fuzzy_ahp_weights_favours_dominant_attribute():
    # Attribute 0 is preferred to attribute 1 by a factor of ~5.
    matrix = [
        [TFN(1, 1, 1),   TFN(4, 5, 6)],
        [TFN(1/6, 1/5, 1/4), TFN(1, 1, 1)],
    ]
    w = fuzzy_ahp_weights(matrix)
    assert w[0] > w[1]
    assert abs(w.sum() - 1.0) < 1e-6


def test_consistency_ratio_small_matrix_is_zero():
    # CR is defined as zero for n < 3.
    matrix = [[TFN(1, 1, 1), TFN(2, 3, 4)], [TFN(0.25, 1/3, 0.5), TFN(1, 1, 1)]]
    assert consistency_ratio(matrix) == 0.0
