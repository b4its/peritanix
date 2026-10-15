"""Bayesian diagnostic fusion + spatial epidemiology (Moran's I).

Fuses independent evidence channels for disease D_i:
    V = visual / trans-illumination posterior   P(V | D_i)
    S = spatial distribution pattern            P(S | D_i)
    T = local topographic wetness (TWI)         P(T | D_i)

    P(D_i | V,S,T) = P(V|D_i) P(S|D_i) P(T|D_i) P(D_i)
                     -------------------------------------
                     sum_j  P(V|D_j) P(S|D_j) P(T|D_j) P(D_j)
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field


@dataclass
class Evidence:
    visual: dict[str, float] = field(default_factory=dict)   # P(V | D_i)
    spatial: dict[str, float] = field(default_factory=dict)  # P(S | D_i)
    twi: dict[str, float] = field(default_factory=dict)      # P(T | D_i)
    prior: dict[str, float] = field(default_factory=dict)    # P(D_i)


def bayesian_fusion(ev: Evidence) -> dict[str, float]:
    """Return unnormalised->normalised posterior. Missing channels default to 1.0
    (uninformative) and missing prior to uniform."""
    diseases = set(ev.visual) | set(ev.spatial) | set(ev.twi) | set(ev.prior)
    if not diseases:
        raise ValueError("no diseases provided")
    if not ev.prior:
        uniform = 1.0 / len(diseases)
        prior = {d: uniform for d in diseases}
    else:
        prior = ev.prior
    post: dict[str, float] = {}
    for d in diseases:
        p = prior.get(d, 0.0)
        p *= ev.visual.get(d, 1.0)
        p *= ev.spatial.get(d, 1.0)
        p *= ev.twi.get(d, 1.0)
        post[d] = p
    z = sum(post.values())
    if z <= 0:
        raise ValueError("zero evidence mass")
    return {d: v / z for d, v in post.items()}


def confidence_level(top_posterior: float) -> str:
    if top_posterior >= 0.85:
        return "HIGH"
    if top_posterior >= 0.6:
        return "MEDIUM"
    return "LOW"


# --- Spatial pattern classification ----------------------------------------

def morans_i(values: list[float], coords: list[tuple[float, float]],
             bandwidth_m: float = 50.0) -> float:
    """Moran's I spatial autocorrelation with a distance-band weight matrix.

    I > 0 => clustered (contagious spread); I ~ 0 => random; I < 0 => dispersed.
    Deterministic; suitable for the small point sets DAPE handles.
    """
    n = len(values)
    if n < 2:
        return 0.0
    mean = sum(values) / n
    dev = [v - mean for v in values]
    # Weight matrix: inverse-distance within bandwidth
    w_sum = 0.0
    num = 0.0
    for i in range(n):
        for j in range(n):
            if i == j:
                continue
            d = _haversine_m(coords[i], coords[j])
            if d <= bandwidth_m and d > 0:
                w = 1.0 / d
                w_sum += w
                num += w * dev[i] * dev[j]
    den = sum(d * d for d in dev)
    if w_sum == 0 or den == 0:
        return 0.0
    return (n / w_sum) * (num / den)


def classify_spatial_pattern(morans_i_value: float, edge_bias: float,
                             trough_bias: float) -> str:
    """Classify the *shape* of an outbreak.

    edge_bias   : fraction of the variance in infection explained by
                  distance-to-land-edge + wind gradient (vector-borne: whitefly,
                  thrips, aphid -> virus / thrips).
    trough_bias : explained by drainage troughs / high-TWI cells (water/soil
                  borne: Phytophthora, Ralstonia, Fusarium).
    """
    if morans_i_value <= 0.05:
        return "UNIFORM_BLOCK"           # likely nutritional deficiency
    if trough_bias >= max(edge_bias, 0.5):
        return "DRAINAGE_TROUGH_CLUSTER"
    if edge_bias >= max(trough_bias, 0.5):
        return "EDGE_WIND_GRADIENT"
    return "CLUSTERED_UNSPECIFIED"


def _haversine_m(a: tuple[float, float], b: tuple[float, float]) -> float:
    lat1, lon1 = a
    lat2, lon2 = b
    r = 6371000.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    h = math.sin(dlat / 2) ** 2 + math.cos(math.radians(lat1)) * \
        math.cos(math.radians(lat2)) * math.sin(dlon / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def twi_likelihood(local_twi: float) -> dict[str, float]:
    """Map local TWI to a per-disease odds multiplier (agronomic heuristics).

    Water/soil-borne pathogens up-weighted in wet (high-TWI) cells; airborne
    fungal (Antraknosa) mildly up-weighted; nutrient deficiency ~ flat.
    """
    if local_twi >= 9.0:
        return {"Ralstonia": 2.2, "Phytophthora": 2.4, "Fusarium": 1.8,
                "Antraknosa": 1.3, "Defisiensi": 0.9}
    if local_twi >= 6.0:
        return {"Ralstonia": 1.4, "Phytophthora": 1.5, "Fusarium": 1.2,
                "Antraknosa": 1.2, "Defisiensi": 1.0}
    return {"Ralstonia": 0.8, "Phytophthora": 0.8, "Fusarium": 0.9,
            "Antraknosa": 1.0, "Defisiensi": 1.1}


def spatial_pattern_likelihood(pattern: str) -> dict[str, float]:
    """Map a classified spatial pattern to P(S | D_i) multipliers.

    pattern is one of the classify_spatial_pattern outputs:
      EDGE_WIND_GRADIENT      -> vector/air-borne entry (whitefly virus, thrips)
      DRAINAGE_TROUGH_CLUSTER -> water/soil-borne (Ralstonia, Phytophthora, Fusarium)
      UNIFORM_BLOCK           -> nutritional/physiological (deficiency)
      CLUSTERED_UNSPECIFIED   -> weakly informative
    Values are odds multipliers (1.0 = uninformative).
    """
    table = {
        "EDGE_WIND_GRADIENT": {
            "Begomovirus": 2.5, "Keriting_Thrips": 2.0, "Keriting_Mites": 1.6,
            "Antraknosa": 1.4, "Ralstonia": 0.7, "Phytophthora": 0.7,
            "Fusarium": 0.8, "Defisiensi": 0.8,
        },
        "DRAINAGE_TROUGH_CLUSTER": {
            "Ralstonia": 2.6, "Phytophthora": 2.6, "Fusarium": 2.2,
            "Antraknosa": 1.2, "Defisiensi": 0.8,
            "Begomovirus": 0.6, "Keriting_Thrips": 0.7, "Keriting_Mites": 0.8,
        },
        "UNIFORM_BLOCK": {
            "Defisiensi": 2.8, "Antraknosa": 1.0, "Ralstonia": 0.8,
            "Phytophthora": 0.8, "Fusarium": 0.9,
            "Begomovirus": 0.9, "Keriting_Thrips": 0.9, "Keriting_Mites": 0.9,
        },
        "CLUSTERED_UNSPECIFIED": {
            "Antraknosa": 1.3, "Ralstonia": 1.2, "Phytophthora": 1.2,
            "Fusarium": 1.1, "Defisiensi": 1.0,
            "Begomovirus": 1.0, "Keriting_Thrips": 1.0, "Keriting_Mites": 1.0,
        },
    }
    return table.get(pattern, {})


def edge_proximity_likelihood(distance_to_edge_m: float,
                              edge_band_m: float = 5.0) -> float:
    """Odds multiplier from proximity to the land edge.

    Points very close to the edge are more consistent with airborne/vector entry
    (spores/viruses blow in from outside). Returns a value in (0, 1]:
    1.0 right at the edge decaying toward 0.3 deep inside.
    """
    if distance_to_edge_m < 0:
        return 1.0  # unknown -> uninformative
    frac = min(1.0, distance_to_edge_m / max(edge_band_m, 1e-9))
    return 1.0 - 0.7 * frac


def wind_alignment_likelihood(bearing_deg: float, prevailing_wind_deg: float,
                              tolerance_deg: float = 60.0) -> float:
    """Odds multiplier for how well a point aligns with the prevailing wind.

    Symptoms downwind of the source are more consistent with vector/air-borne
    spread. Returns ~1.5 when aligned, ~0.7 when opposed.
    """
    if bearing_deg < 0 or prevailing_wind_deg < 0:
        return 1.0
    diff = abs(((bearing_deg - prevailing_wind_deg + 180) % 360) - 180)
    if diff <= tolerance_deg:
        return 1.5
    if diff >= 180 - tolerance_deg:
        return 0.7
    # Linear interpolation between the two extremes.
    t = (diff - tolerance_deg) / max(180 - 2 * tolerance_deg, 1e-9)
    return 1.5 - t * (1.5 - 0.7)
