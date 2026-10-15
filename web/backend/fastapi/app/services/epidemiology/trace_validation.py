"""Physical-evidence validation — gyroscope/accelerometer immersion trace.

To prevent a farmer from simply skipping the 180 s hot-water sterilisation
timer, the mobile app records a sensor trace during the immersion and embeds it
in the signed certificate. This module validates that trace server-side:

- **duration**: the trace must span ~180 s (tolerance ±20 s).
- **motion**: a real immersion involves handling (picking fruit in/out,
  stirring), so the angular-velocity variance must exceed a floor. A perfectly
  still phone is physically implausible.
- **not-constant**: reject traces that are obviously synthetic (all zeros or one
  repeated value) — a naive cheat.

Returns a verdict + score. This is a heuristic deterrent, not proof; it is
combined with the SHA-256 image hash and Ed25519 signature in the certificate.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class TraceSample:
    t: float          # seconds from start
    gx: float         # gyro x (rad/s)
    gy: float
    gz: float
    ax: float = 0.0   # accel x (m/s^2), optional
    ay: float = 0.0
    az: float = 0.0


@dataclass(frozen=True)
class TraceVerdict:
    valid: bool
    duration_s: float
    samples: int
    motion_variance: float
    score: float
    reasons: list[str]


def _variance(xs: list[float]) -> float:
    n = len(xs)
    if n < 2:
        return 0.0
    mean = sum(xs) / n
    return sum((x - mean) ** 2 for x in xs) / n


def validate_trace(samples: list[TraceSample],
                   target_s: float = 180.0,
                   duration_tol_s: float = 20.0,
                   min_motion_variance: float = 1e-4) -> TraceVerdict:
    """Validate an immersion sensor trace."""
    reasons: list[str] = []
    if len(samples) < 5:
        return TraceVerdict(valid=False, duration_s=0.0, samples=len(samples),
                            motion_variance=0.0, score=0.0,
                            reasons=["trace terlalu pendek (< 5 sampel)"])

    times = [s.t for s in samples]
    duration = max(times) - min(times)
    if abs(duration - target_s) > duration_tol_s:
        reasons.append(f"durasi {duration:.0f}s di luar target {target_s:.0f}±{duration_tol_s:.0f}s")

    # Aggregate angular speed across axes.
    speeds = [(s.gx ** 2 + s.gy ** 2 + s.gz ** 2) ** 0.5 for s in samples]
    var = _variance(speeds)

    # Detect a flat (synthetic) trace.
    gyro_vals = [s.gx for s in samples] + [s.gy for s in samples] + [s.gz for s in samples]
    is_constant = (max(gyro_vals) - min(gyro_vals)) <= 1e-9

    if is_constant:
        reasons.append("sinyal giroskop konstan (indikasi lewatkan timer)")
        var = 0.0
    if var < min_motion_variance:
        reasons.append(f"variasi gerak {var:.2e} < ambang {min_motion_variance:.2e}")

    # Score: duration closeness + motion.
    dur_score = max(0.0, 1.0 - abs(duration - target_s) / max(target_s, 1e-9))
    motion_score = min(1.0, var / (min_motion_variance * 10))
    score = round(0.5 * dur_score + 0.5 * motion_score, 3)

    valid = not reasons and score >= 0.5
    if valid:
        reasons.append("bukti fisik wajar: durasi & gerak sesuai")

    return TraceVerdict(
        valid=valid,
        duration_s=round(duration, 2),
        samples=len(samples),
        motion_variance=round(var, 8),
        score=score,
        reasons=reasons,
    )
