# ACOCA-P

**Adaptive Context Caching Architecture — Parameter-driven**

Reference implementation of the ACOCA-P framework described in the PhD thesis
*Parameters Impacting Context Caching for Real-Time Context-Aware IoT Applications*
(Ashish Manchanda, Swinburne University of Technology, 2026).

ACOCA-P is a parameter-driven adaptive context caching framework that runs on
top of the [CoaaS](https://github.com/CoaaS-Project) Context Management Platform.
It monitors four context-caching parameters at runtime and coordinates caching
decisions across the placement, refreshment, refinement, replacement, and
eviction stages of the caching lifecycle.

## Components

| Component | Language   | Role                                                    | Parameter          | Lifecycle stage             |
|-----------|------------|---------------------------------------------------------|--------------------|-----------------------------|
| **CAPME** | Python 3.8 | Context Access Probability Monitoring Engine            | PoA                | Placement                   |
| **CFMS**  | Python 3.8 | Context Freshness Monitoring System                     | CF                 | Refreshment                 |
| **DCMF**  | Java 11    | Dynamic Context Monitoring Framework                    | Combined CF + PoA  | Refinement                  |
| **VACF**  | Java 11    | Volatility-Aware Context Caching Framework              | CVI                | Replacement + Eviction      |
| **ECCF**  | Java 11    | Edge Context Caching Framework                          | CV                 | Distributed (all stages)    |

CAPME + CFMS + DCMF + VACF constitute the centralised ACOCA-P deployment.
ECCF is the distributed edge realisation that removes the central controller.

## Repository layout

```
ACOCA-P/
├── proto/                  # Shared protobuf service definitions
│   └── acoca.proto
├── capme/                  # CAPME (Python + Flask + TensorFlow)
├── cfms/                   # CFMS (Python + Flask + Fuzzy AHP)
├── dcmf/                   # DCMF (Java + Spring Boot + gRPC)
├── vacf/                   # VACF (Java + Spring Boot + gRPC)
├── eccf/                   # ECCF (Java + gossip + Count-Min Sketch)
├── docker-compose.yml      # Bring the whole stack up
├── Makefile                # Convenience targets
└── .env.example            # Environment configuration template
```

## Quick start

```bash
cp .env.example .env
make build         # build every component's container
make up            # docker compose up
make smoke         # run end-to-end smoke test through the CoaaS query engine
```

The stack expects a running MongoDB (cache path) and PostgreSQL (administrative
records). Both are declared in `docker-compose.yml` and provisioned on `make up`.

## Testbed

The centralised evaluation in the thesis is reproduced on a Melbourne testbed:

- **Controller** (VACF-Core): Intel Core i7-6700, 32 GB RAM, at a regional PoP.
- **Edge nodes**: 3 × Raspberry Pi 5 (8 GB RAM, 64 GB microSD), representing
  Hawthorn, Burwood, and Malvern.
- **Cameras**: 4 × Nerian SceneScan 3D depth cameras at roadwork sites,
  emulated in this repo by the [IoT Data Simulator](https://github.com/IBA-Group-IT/IoT-data-simulator).

Dataset: 268,600 context items across six provider families (roadwork,
vehicles, incidents, weather, speed zones, signals). See §9.2.3 of the thesis.

## Configuration

Each component has its own `config.py` (Python) or `application.properties`
(Java). The most operationally significant knobs are:

- **CFMS** — `KAPPA`, sensitivity of the adaptive freshness threshold
  `θ = μ_CF − κ·σ_CF`. Default `1.5`.
- **CAPME** — `EPSILON_DECAY`, ε-greedy policy decay rate for the DQN.
  Default `0.995`.
- **DCMF** — `BELIEF_THRESHOLD`, minimum combined belief for admission.
  Default `0.6`.
- **VACF** — `TAU_MIN`, `TAU_MAX`, TTL bounds. Defaults `30 s` and `300 s`.
- **ECCF** — `T_GOSSIP`, `T_SYNC`, gossip periods. Defaults `2 s` and `60 s`.

## Publications

- IEEE Access 2024 — Adaptive Context Monitoring Framework (CFMS + CAPME survey).
- IEEE CLOUD 2024 — Hybrid strategy for dynamically monitoring access probability (CAPME).
- MobiQuitous 2023 — Hybrid approach to monitor context parameters (CFMS).
- IEEE/ACM CCGrid 2026 — VACF.
- IEEE MDM 2026 — ECCF (Best Paper Award).

## License

MIT.
