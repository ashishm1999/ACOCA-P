#!/usr/bin/env python3
"""Generate a realistic 280,000-record roadwork dataset for the ACOCA-P testbed.

Simulates the output of four Nerian SceneScan 3D depth cameras at four
Melbourne roadwork sites (Hawthorn, Burwood, Malvern, plus a fourth
inner-city control site). Each record represents one aggregated frame
capture (~1 sample per second per camera across a 24-hour window).

Records are emitted in JSON Lines format so the file can be streamed by
the IoT Data Simulator or replayed line-by-line into MongoDB.

Usage
-----
    python3 generate_dataset.py --n 280000 --out roadwork_dataset.jsonl

Seeded for reproducibility (see --seed).
"""

from __future__ import annotations

import argparse
import json
import math
import random
import time
from pathlib import Path


# --- Site metadata (Melbourne testbed) -----------------------------------

SITES = [
    {
        "site_id": "hawthorn-glenferrie-rd",
        "camera_id": "nerian-h1",
        "suburb": "Hawthorn",
        "lat": -37.8218,
        "lon": 145.0421,
        "road": "Glenferrie Rd near Auburn Rd",
        "speed_limit_kph": 40,
        "lanes": 2,
    },
    {
        "site_id": "burwood-highway-warrigal",
        "camera_id": "nerian-b1",
        "suburb": "Burwood",
        "lat": -37.8517,
        "lon": 145.1178,
        "road": "Burwood Hwy at Warrigal Rd",
        "speed_limit_kph": 60,
        "lanes": 3,
    },
    {
        "site_id": "malvern-dandenong-rd",
        "camera_id": "nerian-m1",
        "suburb": "Malvern",
        "lat": -37.8697,
        "lon": 145.0479,
        "road": "Dandenong Rd westbound",
        "speed_limit_kph": 70,
        "lanes": 3,
    },
    {
        "site_id": "cbd-swanston-collins",
        "camera_id": "nerian-c1",
        "suburb": "Melbourne CBD",
        "lat": -37.8154,
        "lon": 144.9660,
        "road": "Swanston St at Collins St",
        "speed_limit_kph": 40,
        "lanes": 2,
    },
]

WEATHER_STATES = ["clear", "cloudy", "overcast", "light-rain", "heavy-rain", "fog"]
WEATHER_WEIGHTS = [0.42, 0.22, 0.14, 0.13, 0.06, 0.03]

INCIDENT_TYPES = ["clear", "lane-closure", "worker-on-road", "equipment-move",
                  "debris", "wet-slip-warning", "signal-outage"]
INCIDENT_WEIGHTS = [0.72, 0.10, 0.05, 0.05, 0.03, 0.03, 0.02]

DETOUR_ROUTES = {
    "hawthorn-glenferrie-rd": ["Barkers Rd", "Riversdale Rd", "Auburn Rd"],
    "burwood-highway-warrigal": ["Toorak Rd", "Whitehorse Rd", "Riversdale Rd"],
    "malvern-dandenong-rd": ["Kooyong Rd", "Wattletree Rd", "Waverley Rd"],
    "cbd-swanston-collins": ["Elizabeth St", "Russell St", "Flinders St"],
}


# --- Diurnal load model --------------------------------------------------

def diurnal_multiplier(hour: float) -> float:
    """Two-peak Melbourne commute profile: morning ~7:30, evening ~17:30."""
    morning = math.exp(-0.5 * ((hour - 7.5) / 1.2) ** 2)
    evening = math.exp(-0.5 * ((hour - 17.5) / 1.4) ** 2)
    offpeak = 0.15
    return offpeak + 0.85 * (morning + evening)


def congestion_score(hour: float, incident: str, weather: str) -> float:
    """0.0 (empty road) to 1.0 (jammed) as a function of hour and events."""
    base = diurnal_multiplier(hour) * 0.6
    if incident in ("lane-closure", "worker-on-road", "signal-outage"):
        base += 0.25
    if incident == "debris":
        base += 0.15
    if weather in ("light-rain", "heavy-rain"):
        base += 0.10
    if weather == "fog":
        base += 0.12
    return max(0.0, min(1.0, base + random.gauss(0.0, 0.05)))


# --- Object detection stub ------------------------------------------------

def synthesise_detections(congestion: float, incident: str) -> list[dict]:
    """A few detected objects per frame, scaled with congestion."""
    n_vehicles = int(round(random.gauss(20 * congestion, 3.0)))
    n_workers = 0
    n_cones = 0
    if incident in ("worker-on-road", "lane-closure", "equipment-move"):
        n_workers = random.randint(1, 4)
        n_cones = random.randint(4, 16)

    out = []
    for i in range(max(0, n_vehicles)):
        out.append({
            "type": random.choices(
                ["car", "truck", "van", "motorcycle", "bicycle", "bus"],
                weights=[0.66, 0.08, 0.10, 0.06, 0.05, 0.05],
            )[0],
            "confidence": round(random.uniform(0.72, 0.99), 3),
            "distance_m": round(random.uniform(3.0, 60.0), 2),
            "velocity_kph": round(random.uniform(0.0, 65.0) * (1.0 - 0.6 * congestion), 1),
            "lane": random.randint(1, 3),
        })
    for _ in range(n_workers):
        out.append({
            "type": "worker",
            "confidence": round(random.uniform(0.85, 0.99), 3),
            "distance_m": round(random.uniform(2.0, 30.0), 2),
            "hi_vis": True,
        })
    for _ in range(n_cones):
        out.append({
            "type": "cone",
            "confidence": round(random.uniform(0.60, 0.95), 3),
            "distance_m": round(random.uniform(2.0, 35.0), 2),
        })
    return out


# --- Depth-frame statistics ----------------------------------------------

def depth_frame_stats(congestion: float) -> dict:
    """Aggregate stats over the 1600x1200 depth image; hides the raw frame."""
    valid = int(random.gauss(1_900_000 * (1 - 0.05 * congestion), 30_000))
    mean_depth = random.uniform(4.5, 22.0)
    return {
        "resolution": "1600x1200",
        "fps": 25,
        "valid_pixels": max(0, valid),
        "invalid_pixels": max(0, 1_920_000 - valid),
        "depth_min_m": round(random.uniform(0.5, 3.0), 2),
        "depth_max_m": round(random.uniform(45.0, 60.0), 2),
        "depth_mean_m": round(mean_depth, 2),
        "depth_std_m": round(random.uniform(1.5, 6.5), 2),
        "confidence_ratio": round(random.uniform(0.82, 0.98), 3),
        "temperature_c": round(random.uniform(18.0, 42.0), 1),
    }


# --- CoaaS-friendly context inference -------------------------------------

def infer_context(site: dict, hour: float, congestion: float, incident: str, weather: str) -> dict:
    """The `context/*` block CoaaS providers surface to consumers."""
    detour = random.choice(DETOUR_ROUTES[site["site_id"]])
    time_delay_min = round(1.5 + congestion * 12.0 + random.gauss(0.0, 0.6), 1)
    congestion_label = (
        "free-flow" if congestion < 0.25
        else "moderate" if congestion < 0.55
        else "congested" if congestion < 0.80
        else "jammed"
    )
    return {
        "roadwork.site_id": site["site_id"],
        "roadwork.location": {"lat": site["lat"], "lon": site["lon"]},
        "roadwork.congestion": congestion_label,
        "roadwork.congestion_score": round(congestion, 3),
        "roadwork.detour": detour,
        "roadwork.time_delay_min": max(0.0, time_delay_min),
        "roadwork.incident": incident,
        "roadwork.weather": weather,
        "roadwork.hour_of_day": round(hour, 2),
        "roadwork.freshness_target": 0.38,
        "roadwork.sla_ms": 250,
    }


# --- One record -----------------------------------------------------------

def one_record(seq: int, ts_ms: int, site: dict) -> dict:
    hour_of_day = (ts_ms / 1000.0 / 3600.0) % 24
    weather = random.choices(WEATHER_STATES, weights=WEATHER_WEIGHTS)[0]
    incident = random.choices(INCIDENT_TYPES, weights=INCIDENT_WEIGHTS)[0]
    congestion = congestion_score(hour_of_day, incident, weather)
    return {
        "@id": f"nerian:frame:{site['camera_id']}:{seq:09d}",
        "@type": "coaas:RoadworkContextItem",
        "@context": {
            "coaas": "http://schema.coaas.org/",
            "schema": "http://schema.org/",
            "sosa": "http://www.w3.org/ns/sosa/",
        },
        "seq": seq,
        "captured_at": ts_ms,
        "captured_at_iso": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(ts_ms / 1000.0)),
        "site": {
            "site_id": site["site_id"],
            "camera_id": site["camera_id"],
            "suburb": site["suburb"],
            "road": site["road"],
            "speed_limit_kph": site["speed_limit_kph"],
            "lanes": site["lanes"],
            "geo": {"lat": site["lat"], "lon": site["lon"]},
        },
        "sensor": {
            "make": "Nerian",
            "model": "SceneScan Pro",
            "firmware": "8.6.2",
            "baseline_m": 0.25,
            "focal_length_mm": 8.0,
        },
        "depth_frame": depth_frame_stats(congestion),
        "detections": synthesise_detections(congestion, incident),
        "context": infer_context(site, hour_of_day, congestion, incident, weather),
        "quality": {
            "signal_to_noise_db": round(random.uniform(22.0, 38.0), 1),
            "occlusion_ratio": round(random.uniform(0.0, 0.15), 3),
            "provider_reputation": round(random.uniform(0.90, 1.00), 3),
        },
    }


# --- Driver ---------------------------------------------------------------

def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--n", type=int, default=280_000, help="records to generate (default 280000)")
    ap.add_argument("--out", type=str, default="roadwork_dataset.jsonl", help="output JSONL file")
    ap.add_argument("--seed", type=int, default=20260703, help="RNG seed")
    ap.add_argument("--start-epoch-s", type=int, default=1_782_432_000,
                    help="epoch seconds for the first record (default: 2026-07-01T00:00Z)")
    args = ap.parse_args()

    random.seed(args.seed)

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    per_site = args.n // len(SITES)
    interval_ms = int(86_400_000 / per_site)   # spread across 24h

    written = 0
    with out_path.open("w", encoding="utf-8") as fh:
        for seq in range(args.n):
            site = SITES[seq % len(SITES)]
            ts_ms = args.start_epoch_s * 1000 + (seq // len(SITES)) * interval_ms
            rec = one_record(seq=seq, ts_ms=ts_ms, site=site)
            fh.write(json.dumps(rec, separators=(",", ":")))
            fh.write("\n")
            written += 1
            if written % 20_000 == 0:
                print(f"... {written:,} records written", flush=True)

    size_mb = out_path.stat().st_size / (1024 * 1024)
    print(f"Done: {written:,} records -> {out_path} ({size_mb:.1f} MB)")


if __name__ == "__main__":
    main()
