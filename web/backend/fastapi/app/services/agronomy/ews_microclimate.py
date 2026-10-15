"""Microclimate Early Warning System — deterministic fungal-blast risk.

Antraknosa (Colletotrichum spp.) sporulation surges when ambient relative
humidity stays above ~80 % at warm temperatures. Rather than a black-box
forecaster, we implement the **agronomically defensible** rule with a short
trend extrapolation:

- Compute a daily *infection-risk index* from hourly (RH, T):
      risk_h = ramp(RH; 80..95) * ramp(T; 15..30) * ramp(T; 35..38 inverted)
  where each ramp is a clipped linear membership.
- Extrapolate RH/T 72 h ahead via a damped linear trend on the last window.
- Report `blast_risk_72h` (0..1), the day it peaks, and an irrigation action.

This mirrors classic disease-warning systems (e.g. BLASTAM / Tom-Cast) and is
fully deterministic (no learned parameters), so a warning can be justified.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class EwsResult:
    blast_risk_72h: float
    peak_risk: float
    peak_hour_offset: int
    mean_rh: float
    mean_t: float
    action: str
    irrigation_minutes_per_bed: int


def _ramp_up(x: float, lo: float, hi: float) -> float:
    if hi <= lo:
        raise ValueError("hi must exceed lo")
    return float(min(1.0, max(0.0, (x - lo) / (hi - lo))))


def _ramp_down(x: float, lo: float, hi: float) -> float:
    return 1.0 - _ramp_up(x, lo, hi)


def hourly_risk(rh: float, t: float) -> float:
    """Fungal-blast risk for one hour in [0, 1].

    RH saturates quickly once above 80 % (90 % is already near-optimal for
    Colletotrichum sporulation); temperature rises to an optimum near 27 °C and
    falls off sharply above 35 °C.
    """
    rh_membership = _ramp_up(rh, 80.0, 90.0)          # RH >= 90 % -> saturated
    t_membership = _ramp_up(t, 15.0, 27.0) * _ramp_down(t, 33.0, 38.0)
    return rh_membership * t_membership


def _damped_trend(series: list[float], damp: float = 0.9) -> float:
    """Per-step slope of the last few points, damped to avoid runaway."""
    if len(series) < 2:
        return 0.0
    window = series[-4:] if len(series) >= 4 else series
    n = len(window)
    xs = list(range(n))
    xbar = sum(xs) / n
    ybar = sum(window) / n
    num = sum((xs[i] - xbar) * (window[i] - ybar) for i in range(n))
    den = sum((xs[i] - xbar) ** 2 for i in range(n)) or 1e-9
    return (num / den) * damp


def analyse_microclimate(rh_series: list[float], t_series: list[float],
                         horizon_h: int = 72) -> EwsResult:
    """Extrapolate risk over the horizon and prescribe irrigation."""
    if len(rh_series) != len(t_series) or len(rh_series) < 3:
        raise ValueError("rh and t series must be equal length and >= 3 samples")

    risks = [hourly_risk(rh, t) for rh, t in zip(rh_series, t_series)]
    current_risk = risks[-1]

    rh_slope = _damped_trend(list(rh_series))
    t_slope = _damped_trend(list(t_series))

    rh_last, t_last = rh_series[-1], t_series[-1]
    peak = current_risk
    peak_off = 0
    for h in range(1, horizon_h + 1):
        # Cap RH at 100 and T into a realistic band.
        rh_h = min(100.0, max(0.0, rh_last + rh_slope * h))
        t_h = min(40.0, max(0.0, t_last + t_slope * h))
        r = hourly_risk(rh_h, t_h)
        if r > peak:
            peak = r
            peak_off = h

    mean_rh = sum(rh_series) / len(rh_series)
    mean_t = sum(t_series) / len(t_series)

    if peak >= 0.7:
        action = ("RISIKO TINGGI ledakan Antraknosa 72 jam ke depan. "
                  "Jadwalkan fungisida protektif (Mankozeb) sore ini dan kurangi "
                  "penyiraman; jaga sirkulasi tajuk.")
        minutes = 0
    elif peak >= 0.4:
        action = ("Risiko sedang. Pantau kelembaban tajuk, hindari penyiraman "
                  "berlebih, siapkan fungisida.")
        minutes = 10
    else:
        action = "Risiko rendah. Penyiraman normal sesuai jadwal."
        minutes = 20

    return EwsResult(
        blast_risk_72h=round(current_risk, 4),
        peak_risk=round(peak, 4),
        peak_hour_offset=peak_off,
        mean_rh=round(mean_rh, 2),
        mean_t=round(mean_t, 2),
        action=action,
        irrigation_minutes_per_bed=minutes,
    )
