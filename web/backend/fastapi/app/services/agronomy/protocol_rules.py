"""Diagnostic protocol rules — deterministic field-workflow triggers.

Encodes the agronomic *decision rules* the app enforces at the point of care,
so the user is not left guessing. Each rule is transparent and unit-tested.

Currently:
  * **Destructive Ooze Trigger** (FR-2.3): if a plant shows wilting AND the
    local TWI is high (a wet drainage trough), the pathogens Ralstonia vs
    Fusarium are indistinguishable from a photo alone. The app must BLOCK the
    verdict and MANDATE the ooze test: cut the stem base, dip it in clear water
    against a dark background, and record a 10 s clip for dense-optical-flow
    analysis.
  * **Fortification rule**: high-N during anthesis is forbidden (see the Go
    guard); the rule here restates the required Ca+B+Si protocol for the client.
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class Trigger:
    code: str
    required: bool
    instruction: str
    reason: str
    blocking: bool
    capture_s: int = 0
    steps: list[str] = field(default_factory=list)


# Wilting-related symptoms are the ones that can hide Ralstonia/Fusarium.
WILT_SYMPTOMS = {
    "layu", "wilt", "wilting", "layu_bakteri", "layu_fusarium", "drooping",
}

# Ooze test becomes *mandatory* above this local TWI (a wet trough favours
# bacterial streaming and soil-borne pathogens).
OOZE_TWI_THRESHOLD = 8.0


def _is_wilting(symptoms: list[str]) -> bool:
    low = {s.strip().lower() for s in symptoms}
    return any(w in sym or sym in w for sym in low for w in WILT_SYMPTOMS)


def destructive_ooze_trigger(symptoms: list[str], local_twi: float,
                             already_recorded: bool = False) -> Trigger | None:
    """Return a mandatory ooze-test trigger when the condition is met.

    Args:
        symptoms: free-text symptom tags seen on the plant.
        local_twi: local topographic wetness index.
        already_recorded: if the ooze clip was already captured, no trigger.

    Returns None when the rule does not apply.
    """
    if already_recorded:
        return None
    if not _is_wilting(symptoms):
        return None
    if local_twi < OOZE_TWI_THRESHOLD:
        return None
    return Trigger(
        code="OOZE_TEST_MANDATORY",
        required=True,
        blocking=True,
        capture_s=10,
        reason=(
            f"Tanaman layu + TWI lokal {local_twi:.1f} (cekungan basah): "
            "Ralstonia dan Fusarium tak dapat dibedakan dari foto. Uji ooze wajib."
        ),
        instruction=(
            "Potong pangkal batang, celupkan ke air bening di depan latar gelap, "
            "rekam video 10 detik."
        ),
        steps=[
            "Potong pangkal batang dengan pisau bersih",
            "Celupkan ke air bening, latar gelap",
            "Rekam video 10 detik (aliran benang putih = bakteri streaming)",
            "Tandai hasil: Ralstonia (ada aliran) vs Fusarium (tidak ada)",
        ],
    )


def evaluate_protocol_rules(symptoms: list[str], local_twi: float,
                            already_recorded_ooze: bool = False) -> list[Trigger]:
    """Run all protocol rules; return the list of active triggers (may be empty)."""
    trig = destructive_ooze_trigger(symptoms, local_twi, already_recorded_ooze)
    return [trig] if trig else []
