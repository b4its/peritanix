"""Growing Degree Days (GDD) — thermal-time physiology for chilli.

    GDD_day = max(0, (T_max + T_min)/2 - T_base),   T_base = 15 C

Accumulated GDD drives *phase transitions* (deterministic, calendar-free):
    VEGETATIVE           : < flowering_start
    FLOWERING            : flowering_start .. fruiting_start
    FRUITING             : fruiting_start  .. harvest_start
    HARVEST              : >= harvest_start
    FORTIFICATION_LOCKED : a *flag* set during peak anthesis (see below — it
                           is orthogonal to thermal phase and enforced by the
                           backend; here we expose the *window*).
"""
from __future__ import annotations

from dataclasses import dataclass

T_BASE_DEFAULT = 15.0


@dataclass(frozen=True)
class GddPhase:
    day: str
    t_min: float
    t_max: float
    gdd_day: float
    gdd_cum: float
    phase: str


def gdd_day(t_min: float, t_max: float, t_base: float = T_BASE_DEFAULT) -> float:
    if t_max < t_min:
        raise ValueError("t_max must be >= t_min")
    return max(0.0, (t_max + t_min) / 2.0 - t_base)


def phase_for_gdd(gdd_cum: float, flowering_start: float = 350.0,
                  fruiting_start: float = 700.0, harvest_start: float = 1050.0) -> str:
    if gdd_cum >= harvest_start:
        return "HARVEST"
    if gdd_cum >= fruiting_start:
        return "FRUITING"
    if gdd_cum >= flowering_start:
        return "FLOWERING"
    return "VEGETATIVE"


def accumulate(series: list[tuple[str, float, float]], t_base: float = T_BASE_DEFAULT,
               flowering_start: float = 350.0, fruiting_start: float = 700.0,
               harvest_start: float = 1050.0, start_cum: float = 0.0) -> list[GddPhase]:
    """Accumulate GDD across a [(day, t_min, t_max), ...] series."""
    out: list[GddPhase] = []
    cum = start_cum
    for day, t_min, t_max in series:
        d = gdd_day(t_min, t_max, t_base)
        cum += d
        out.append(GddPhase(day=day, t_min=t_min, t_max=t_max, gdd_day=round(d, 2),
                            gdd_cum=round(cum, 2),
                            phase=phase_for_gdd(cum, flowering_start, fruiting_start,
                                                harvest_start)))
    return out


def is_fortification_window(gdd_cum: float, flowering_start: float = 350.0,
                            fruiting_start: float = 700.0) -> bool:
    """Peak-anthesis window where Ca+B+Si cell-wall fortification is mandatory
    and high-N (urea) is forbidden."""
    return flowering_start <= gdd_cum < fruiting_start
