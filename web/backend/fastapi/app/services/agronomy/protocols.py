"""Prescriptive protocol catalogue — verdict/severity → actionable protocol.

Turns a diagnosis (disease + severity) into a concrete, offline-usable action:
the active ingredient, dose per litre, application window, interval, and safety
notes. Severity modulates the dose (a mild infection gets a lower rate than a
severe one) following the labelled range, never exceeding it.

This is a *rule engine*, deliberately transparent and auditable — a small
language model would hallucinate doses, which is unacceptable for agrochemicals.
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class Protocol:
    code: str
    disease: str
    active_ingredient: str
    dose_min_g_per_l: float
    dose_max_g_per_l: float
    interval_days: int
    window: str
    safety: str
    organic_fallback: str = ""


_CATALOG: dict[str, Protocol] = {
    "FUNG_MANKOZEB_AZOXY": Protocol(
        code="FUNG_MANKOZEB_AZOXY",
        disease="Antraknosa (Colletotrichum spp.)",
        active_ingredient="Mankozeb 80% + Azoksistrobin 250 g/L",
        dose_min_g_per_l=2.0, dose_max_g_per_l=2.5, interval_days=7,
        window="sore (16:00–18:00), hindari hujan < 4 jam",
        safety="Gunakan APD; jangan semprot saat berbunga penuh (membunuh penyerbuk).",
        organic_fallback="Ekstrak bawang putih + kunyit, atau Trichoderma tanah.",
    ),
    "FUNG_METALAXYL_DRAINAGE": Protocol(
        code="FUNG_METALAXYL_DRAINAGE",
        disease="Phytophthora (busuk akar/buah)",
        active_ingredient="Metalaksil 35% (kocor) atau Fosetyl-Al",
        dose_min_g_per_l=2.0, dose_max_g_per_l=3.0, interval_days=10,
        window="pagi, dikocor ke perakaran",
        safety="Perbaiki drainase; jangan over-watering pasca aplikasi.",
        organic_fallback="Trichoderma harzianum + perbaikan drainase bedengan.",
    ),
    "BACT_SANITIZE_BURN_CROP_ROTATION": Protocol(
        code="BACT_SANITIZE_BURN_CROP_ROTATION",
        disease="Ralstonia solanacearum (layu bakteri)",
        active_ingredient="Tidak ada bakterisida efektif — eradikasi mekanis",
        dose_min_g_per_l=0.0, dose_max_g_per_l=0.0, interval_days=0,
        window="segera setelah konfirmasi",
        safety="Cabut & bakar tanaman sakit; sterilkan alat; rotasi non-solanaceae ≥ 2 musim.",
        organic_fallback="Solarisasi tanah + kapur pertanian untuk menaikkan pH.",
    ),
    "FUNG_TRICHODERMA_ROTATION": Protocol(
        code="FUNG_TRICHODERMA_ROTATION",
        disease="Fusarium oxysporum (layu fusarium)",
        active_ingredient="Trichoderma sp. (biokontrol) + rotasi",
        dose_min_g_per_l=5.0, dose_max_g_per_l=10.0, interval_days=14,
        window="kocor ke zona perakaran, pagi",
        safety="Kurangi kelembaban berlebih; gunakan varietas toleran bila ada.",
        organic_fallback="Kompos matang + mulsa organik untuk musuh alami.",
    ),
    "INSEK_ABAMEKTIN": Protocol(
        code="INSEK_ABAMEKTIN",
        disease="Keriting daun — Thrips",
        active_ingredient="Abamektin 18 g/L",
        dose_min_g_per_l=0.25, dose_max_g_per_l=0.5, interval_days=5,
        window="pagi (thrips aktif pagi), semprot underside daun",
        safety="Rotasi bahan aktif (jangan >2× berturut) cegah resistensi.",
        organic_fallback="Predator Orius + ekstrak mimba (neem).",
    ),
    "AKARISIDA_ABAMEKTIN": Protocol(
        code="AKARISIDA_ABAMEKTIN",
        disease="Keriting daun — Tungau/Mites",
        active_ingredient="Abamektin / Bifenazat (akarisida)",
        dose_min_g_per_l=0.25, dose_max_g_per_l=0.5, interval_days=5,
        window="pagi/sore, semprot silang bawah daun",
        safety="Tungau cepat resisten: rotasi kelas akarisida tiap aplikasi.",
        organic_fallback="Belerang (sulfur) 2 g/L + predator Phytoseiulus.",
    ),
    "VECTOR_CONTROL_WHITEFLY": Protocol(
        code="VECTOR_CONTROL_WHITEFLY",
        disease="Begomovirus (Virus Kuning) via Bemisia tabaci",
        active_ingredient="Pengendalian vektor: Imidakloprid / Paelobutrazol-free",
        dose_min_g_per_l=0.25, dose_max_g_per_l=0.5, interval_days=7,
        window="fokus kelola kutu kebul (vektor), bukan virusnya",
        safety="Virus tak dapat disembuhkan: cabut tanaman bergejala berat; kelola vektor.",
        organic_fallback="Mulsa reflektif perak + perangkap kuning lengket.",
    ),
    "NUTRI_FOLIAR_BALANCE": Protocol(
        code="NUTRI_FOLIAR_BALANCE",
        disease="Defisiensi nutrisi (bukan patogen)",
        active_ingredient="Pupuk daun seimbang + koreksi pH tanah",
        dose_min_g_per_l=2.0, dose_max_g_per_l=3.0, interval_days=10,
        window="pagi, semprot foliar",
        safety="Cek pH tanah dulu (dolomit bila < 5.5); pastikan N bukan penyebab.",
        organic_fallback="Kompos + pupuk kandang matang.",
    ),
    "GENERAL_SCOUT": Protocol(
        code="GENERAL_SCOUT",
        disease="Belum terkonfirmasi",
        active_ingredient="Lakukan skouting & kirim sampel",
        dose_min_g_per_l=0.0, dose_max_g_per_l=0.0, interval_days=0,
        window="—",
        safety="Jangan semprot blind; konfirmasi diagnosis dulu untuk hindari resistensi.",
        organic_fallback="",
    ),
}


@dataclass
class Prescription:
    code: str
    disease: str
    active_ingredient: str
    dose_g_per_l: float
    unit: str
    interval_days: int
    window: str
    safety: str
    organic_fallback: str
    severity_pct: float
    action_line: str
    steps: list[str] = field(default_factory=list)


def _severity_frac(severity_pct: float) -> float:
    """Map severity 0..100 % to a dose multiplier in [0, 1] of the range."""
    return max(0.0, min(1.0, severity_pct / 100.0))


def prescribe(verdict_key: str, severity_pct: float = 0.0,
              confidence: str = "MEDIUM") -> Prescription:
    """Generate a concrete prescription for a verdict.

    `verdict_key` is the protocol code (recommended_protocol_code) from the
    Bayesian verdict, or a bare disease name mapped to it.
    """
    proto = _CATALOG.get(verdict_key)
    if proto is None:
        proto = _CATALOG["GENERAL_SCOUT"]

    span = proto.dose_max_g_per_l - proto.dose_min_g_per_l
    dose = proto.dose_min_g_per_l + span * _severity_frac(severity_pct)
    if proto.dose_max_g_per_l == 0.0:
        dose = 0.0

    steps: list[str] = []
    if "Antraknosa" in proto.disease:
        steps = [
            "Petik & bakar buah terinfeksi (jangan tinggalkan di bedengan)",
            f"Campur {proto.active_ingredient} sesuai dosis lalu semprot merata",
            "Ulangi tiap 7 hari, rotasi bahan aktif setelah 2 aplikasi",
        ]
    elif "Ralstonia" in proto.disease:
        steps = [
            "Cabut total tanaman layu, akar sampai bersih",
            "Kumpulkan & bakar di luar lahan, jangan dijadikan kompos",
            "Sterilkan alat dengan alkohol 70%; tandai zona karantina",
        ]
    elif proto.code in ("INSEK_ABAMEKTIN", "AKARISIDA_ABAMEKTIN", "VECTOR_CONTROL_WHITEFLY"):
        steps = [
            "Cek permukaan bawah daun (lokasi hama beserta telur)",
            f"Semprot {proto.active_ingredient} fokus underside",
            "Pasang perangkap kuning; rotasi bahan aktif tiap aplikasi",
        ]
    else:
        steps = [proto.safety]

    if proto.dose_max_g_per_l > 0:
        action_line = (f"{proto.disease}: {proto.active_ingredient} "
                       f"dosis {dose:.2f} g/L, {proto.window}, ulang {proto.interval_days} hari.")
    else:
        action_line = f"{proto.disease}: {proto.safety}"

    return Prescription(
        code=proto.code,
        disease=proto.disease,
        active_ingredient=proto.active_ingredient,
        dose_g_per_l=round(dose, 3),
        unit="g/L",
        interval_days=proto.interval_days,
        window=proto.window,
        safety=proto.safety,
        organic_fallback=proto.organic_fallback,
        severity_pct=severity_pct,
        action_line=action_line,
        steps=steps,
    )


def catalog() -> list[Protocol]:
    """Return the full catalogue (for the web UI)."""
    return sorted(_CATALOG.values(), key=lambda p: p.code)
