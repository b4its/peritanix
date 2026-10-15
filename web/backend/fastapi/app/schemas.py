"""DAPE FastAPI — Pydantic request/response schemas."""
from __future__ import annotations

from pydantic import BaseModel, Field


# --- GIS / TWI --------------------------------------------------------------
class BBoxRequest(BaseModel):
    land_id: str
    min_lon: float
    min_lat: float
    max_lon: float
    max_lat: float


class TwiCell(BaseModel):
    lon: float
    lat: float
    elevation_m: float
    upslope_area: float
    twi: float
    risk: str
    bed_height_cm: int


class TwiResponse(BaseModel):
    land_id: str
    grid_rows: int
    grid_cols: int
    cells: list[TwiCell]
    mean_twi: float
    max_twi: float
    max_risk: str
    cache_kb: float


# --- Soil -------------------------------------------------------------------
class StokesLayers(BaseModel):
    sand_thickness: float
    silt_thickness: float
    clay_thickness: float


class SoilCalibrateRequest(BaseModel):
    land_id: str
    layers: StokesLayers
    dark_chamber_lux_check: bool = True


class SoilCalibrateResponse(BaseModel):
    land_id: str
    sand_pct: float
    silt_pct: float
    clay_pct: float
    soil_class: str
    cec_meq: float
    fertilizer_base_note: str


class PhotometryRequest(BaseModel):
    measured_rgb: tuple[float, float, float]
    white_ref_rgb: tuple[float, float, float] = (255.0, 255.0, 255.0)
    # calibration curve: list of {value, lab:[l,a,b]}
    curve: list[dict] = Field(default_factory=list)


class PhotometryResponse(BaseModel):
    corrected_rgb: tuple[float, float, float]
    lab: tuple[float, float, float]
    matched_value: float | None
    nanometres_note: str


# --- Bayesian diagnosis -----------------------------------------------------
class BayesianDiagnosisRequest(BaseModel):
    land_id: str
    point: tuple[float, float]  # lon, lat
    visual: dict[str, float] = Field(default_factory=dict)
    spatial: dict[str, float] = Field(default_factory=dict)
    local_twi: float | None = None
    prior: dict[str, float] = Field(default_factory=dict)
    ooze_optical_flow: dict | None = None
    trans_illumination_attenuation_pct: float | None = None
    severity_pct: float | None = None


class BayesianDiagnosisResponse(BaseModel):
    posterior_probabilities: dict[str, float]
    final_verdict: str
    confidence_level: str
    recommended_protocol_code: str
    physics_metrics: dict


# --- GDD --------------------------------------------------------------------
class GddRequest(BaseModel):
    land_id: str
    series: list[tuple[str, float, float]]  # day, t_min, t_max
    start_cum: float = 0.0


class GddResponse(BaseModel):
    land_id: str
    accumulated_gdd: float
    current_phase: str
    fortification_locked: bool
    series: list[dict]


# --- Calorimetry ------------------------------------------------------------
class CalorimetryRequest(BaseModel):
    elevation_m: float
    well_temp_c: float = 27.0
    target_c: float = 52.0
    scale: float = 9.0


class CalorimetryResponse(BaseModel):
    boiling_point_c: float
    target_c: float
    v_boil_parts: float
    v_well_parts: float
    ratio_label: str
    tts_text: str
    countdown_s: int


# --- Archimedes / grading ---------------------------------------------------
class ArchimedesRequest(BaseModel):
    sink_count: int
    neutral_count: int = 0
    float_count: int = 0
    salt_g: float = 0.0
    water_ml: float = 0.0


class ArchimedesResponse(BaseModel):
    sink_ratio: float
    physics_grade: str
    brine_relative_density: float | None


# --- Fertilizer prescription ------------------------------------------------
class FertilizerRequest(BaseModel):
    ph: float
    cec_meq: float
    hst: int
    target_yield_t_ha: float = 20.0
    soil_class: str = "loam"


class FertilizerResponse(BaseModel):
    npk_16_16_16_g_per_l: float
    kno3_g_per_l: float
    calcium_g_per_l: float
    recommendation: str
