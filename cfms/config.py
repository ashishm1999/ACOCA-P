"""Runtime configuration for CFMS."""

import os


def _f(name: str, default: float) -> float:
    return float(os.environ.get(name, default))


def _i(name: str, default: int) -> int:
    return int(os.environ.get(name, default))


class Config:
    PORT: int = _i("CFMS_PORT", 8082)

    # Statistical adaptive threshold θ = μ_CF − κ · σ_CF.
    # κ trades false-refresh (small κ) against stale-serve (large κ).
    KAPPA: float = _f("CFMS_KAPPA", 1.5)

    # Sliding window over which PFPA maintains streaming statistics.
    WINDOW_SIZE: int = _i("CFMS_WINDOW_SIZE", 100)

    # Exponential decay coefficient λ in CF(t) = exp(-λ · Δt).
    # λ = 0.01 → half-life ~ 69 s.
    LAMBDA_DECAY: float = _f("CFMS_LAMBDA_DECAY", 0.01)

    # Roadwork scenario default attribute set (Chapter 6).
    ATTRIBUTES = ("roadwork_sign", "location", "speed", "congestion", "time_delay", "detour")

    MONGO_URI: str = os.environ.get("MONGO_URI", "mongodb://mongo:27017/acoca")
