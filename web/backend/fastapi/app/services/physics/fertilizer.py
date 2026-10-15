"""Prescriptive fertilizer calculator — deterministic agronomic rules.

A LightGBM model is trained offline (see scripts/train_fertilizer.py) and
exported to ONNX for on-device inference. Here we provide a deterministic,
auditable *rule engine* that is the ground truth the ML model is fitted to
(and the fallback when no ONNX model is bundled).

Rules encode the DAPE agronomic thesis:
  - pH < 5.5      -> P locked out; raise pH with dolomite before N/K.
  - Vegetative    -> N-leaning, modest K.
  - Flowering     -> Ca + B critical (anti blossom-end-rot); forbid high urea.
  - Fruiting      -> K-leaning (KNO3), steady Ca.
Inputs: pH, CEC, HST (or GDD phase), target yield.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class FertilizerPlan:
    npk_16_16_16_g_per_l: float
    kno3_g_per_l: float
    calcium_g_per_l: float
    dolomite_g_per_m2: float
    recommendation: str


def _phase(hst: int) -> str:
    if hst < 35:
        return "VEGETATIVE"
    if hst < 61:
        return "FLOWERING"
    return "FRUITING"


def prescribe(ph: float, cec_meq: float, hst: int,
              target_yield_t_ha: float = 20.0, soil_class: str = "loam") -> FertilizerPlan:
    phase = _phase(hst)

    # Base NPK scaled by soil CEC (higher CEC buffers nutrients) and target yield.
    cec_factor = max(0.6, min(1.6, 15.0 / max(cec_meq, 1.0)))
    yield_factor = max(0.8, min(1.5, target_yield_t_ha / 20.0))
    base_npk = 3.0 * cec_factor * yield_factor

    kno3 = 0.0
    calcium = 0.0
    dolomite = 0.0
    notes: list[str] = []

    if ph < 5.5:
        dolomite = 150.0 * (5.8 - ph)  # g/m2 to lift pH toward ~6.0
        notes.append(f"pH {ph:.1f} terlalu masam: tabur dolomit {dolomite:.0f} g/m2 "
                     f"2 minggu sebelum pemupukan agar P & Ca tidak terikat.")

    if phase == "VEGETATIVE":
        npk = base_npk
        notes.append("Fase vegetatif: prioritaskan N untuk pertumbuhan daun & batang.")
    elif phase == "FLOWERING":
        npk = base_npk * 0.6
        calcium = 2.5
        kno3 = 1.0
        notes.append("Fase pembungaan: WAJIB Ca+B (cegah rontok bunga & busuk ujung). "
                     "HINDARI urea/N tinggi (memperlemah dinding sel).")
    else:  # FRUITING
        npk = base_npk * 0.4
        kno3 = 3.0
        calcium = 2.0
        notes.append("Fase pembuahan: dominan K (KNO3) untuk bobot & warna buah, "
                     "Ca tetap dijaga untuk ketebalan pericarp.")

    return FertilizerPlan(
        npk_16_16_16_g_per_l=round(npk, 2),
        kno3_g_per_l=round(kno3, 2),
        calcium_g_per_l=round(calcium, 2),
        dolomite_g_per_m2=round(dolomite, 2),
        recommendation=" ".join(notes),
    )
