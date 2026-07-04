"""Deep Q-Network agent for MAUT weight refinement.

Formulation (Chapter 5 of the thesis):

    state   s_t = (hit_rate, miss_rate, avg_freshness, avg_recency, avg_load, epsilon)
    action  a_t ∈ {up_i, down_i for each attribute i}   (12 discrete actions)
    reward  r_t = Δ(cache hit ratio) - λ · Δ(cache expired ratio)

The agent uses a target network synchronised every `TARGET_SYNC_STEPS`
gradient updates and an experience replay buffer of `REPLAY_CAPACITY`
transitions to stabilise training under the non-stationary workload.
"""

from __future__ import annotations

import random
from collections import deque
from typing import Deque, Tuple

import numpy as np

try:
    import tensorflow as tf
    from tensorflow import keras
    _TF_AVAILABLE = True
except ImportError:  # tests may run without TF installed
    _TF_AVAILABLE = False

from config import Config


STATE_DIM = 6
STEP_SIZE = 0.05
ACTION_SPACE = tuple(
    (attr, sign)
    for attr in Config.ATTRIBUTES
    for sign in (+1, -1)
)  # 12 discrete actions


Transition = Tuple[np.ndarray, int, float, np.ndarray, bool]


class ReplayBuffer:
    """Bounded FIFO of transitions, uniform-sampled for minibatch updates."""

    def __init__(self, capacity: int) -> None:
        self._buf: Deque[Transition] = deque(maxlen=capacity)

    def push(self, t: Transition) -> None:
        self._buf.append(t)

    def sample(self, batch_size: int) -> list[Transition]:
        return random.sample(self._buf, min(batch_size, len(self._buf)))

    def __len__(self) -> int:
        return len(self._buf)


def _build_network() -> "keras.Model":
    """A small MLP: 6 -> 64 -> 64 -> |actions|."""
    if not _TF_AVAILABLE:
        raise RuntimeError("TensorFlow is not available in this environment")
    return keras.Sequential(
        [
            keras.layers.Input(shape=(STATE_DIM,)),
            keras.layers.Dense(64, activation="relu"),
            keras.layers.Dense(64, activation="relu"),
            keras.layers.Dense(len(ACTION_SPACE)),
        ]
    )


class DQNAgent:
    """ε-greedy DQN agent driving MAUT weight refinement."""

    def __init__(self, weights: np.ndarray) -> None:
        self.weights = weights.astype(np.float32)
        self.epsilon = Config.EPSILON_START
        self.step = 0
        self.replay = ReplayBuffer(Config.REPLAY_CAPACITY)
        if _TF_AVAILABLE:
            self.online = _build_network()
            self.target = _build_network()
            self.target.set_weights(self.online.get_weights())
            self.online.compile(
                optimizer=keras.optimizers.Adam(learning_rate=Config.LEARNING_RATE),
                loss="mse",
            )
        else:
            self.online = None
            self.target = None

    # --- Action selection -------------------------------------------------

    def select_action(self, state: np.ndarray) -> int:
        """ε-greedy action selection. Returns index into ACTION_SPACE."""
        if random.random() < self.epsilon or self.online is None:
            return random.randrange(len(ACTION_SPACE))
        q = self.online.predict(state[None, :], verbose=0)
        return int(np.argmax(q[0]))

    def apply_action(self, action_idx: int) -> np.ndarray:
        """Apply the chosen action to the MAUT weight vector.

        Each action is a (+1, -1) nudge to one attribute weight by STEP_SIZE.
        Returns the new weight vector (unnormalised — the MAUT module will
        project onto the simplex before scoring).
        """
        attr, sign = ACTION_SPACE[action_idx]
        idx = Config.ATTRIBUTES.index(attr)
        self.weights = self.weights.copy()
        self.weights[idx] = max(0.0, self.weights[idx] + sign * STEP_SIZE)
        return self.weights

    # --- Training ---------------------------------------------------------

    def observe(self, transition: Transition) -> None:
        self.replay.push(transition)

    def train(self) -> float:
        """Run one minibatch gradient update. Returns the mean loss."""
        if self.online is None or len(self.replay) < Config.BATCH_SIZE:
            return 0.0
        batch = self.replay.sample(Config.BATCH_SIZE)
        s = np.array([t[0] for t in batch], dtype=np.float32)
        a = np.array([t[1] for t in batch], dtype=np.int32)
        r = np.array([t[2] for t in batch], dtype=np.float32)
        s2 = np.array([t[3] for t in batch], dtype=np.float32)
        done = np.array([t[4] for t in batch], dtype=np.float32)

        q_next = self.target.predict(s2, verbose=0)
        target = r + (1.0 - done) * Config.DISCOUNT_FACTOR * np.max(q_next, axis=1)

        q_current = self.online.predict(s, verbose=0)
        for i, ai in enumerate(a):
            q_current[i, ai] = target[i]

        history = self.online.fit(s, q_current, verbose=0)
        loss = float(history.history["loss"][0])

        self.step += 1
        if self.step % Config.TARGET_SYNC_STEPS == 0:
            self.target.set_weights(self.online.get_weights())
        self.epsilon = max(Config.EPSILON_MIN, self.epsilon * Config.EPSILON_DECAY)
        return loss
