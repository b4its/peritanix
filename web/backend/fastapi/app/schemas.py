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
    # PostGIS-derived spatial context (nearby, edge distance, bearing, wind, pattern)
    spatial_context: dict | None = None


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


# --- Multi-modal optics / epidemiology --------------------------------------
class MfsrRequest(BaseModel):
    # Grayscale frames as nested lists: [ [ [row], ... ], ... ]
    frames: list[list[list[float]]]
    sharpness_reject_ratio: float = 0.5


class MfsrResponse(BaseModel):
    stacked_shape: tuple[int, int]
    frames_used: int
    frames_rejected: int
    shifts: list[tuple[float, float]]
    sharpness_gain: float
    stacked_sharpness: float


class TransIllumRequest(BaseModel):
    intensity: list[list[float]]
    sigma_k: float = 2.0


class TransIllumResponse(BaseModel):
    attenuation_pct: float
    lesion_fraction: float
    mean_od: float
    max_od: float
    latent_fungal_suspected: bool


class OozeRequest(BaseModel):
    frames: list[list[list[float]]]


class OozeResponse(BaseModel):
    mean_speed: float
    coherence: float
    directionality: float
    streaming_score: float
    verdict: str
    confidence: float


# --- Post-harvest grading ---------------------------------------------------
class GradeRequest(BaseModel):
    contour: list[tuple[float, float]]
    mean_a_channel: float
    lesion_area: float
    total_area: float


class GradeResponse(BaseModel):
    curvature_ratio: float
    red_pct: float
    defect_pct: float
    grade: str
    reasons: list[str]


# --- Microclimate EWS -------------------------------------------------------
class EwsRequest(BaseModel):
    rh_series: list[float]
    t_series: list[float]
    horizon_h: int = 72


class EwsResponse(BaseModel):
    blast_risk_72h: float
    peak_risk: float
    peak_hour_offset: int
    mean_rh: float
    mean_t: float
    action: str
    irrigation_minutes_per_bed: int


# --- Anti-fraud forensics ---------------------------------------------------
class ForensicRequest(BaseModel):
    image_base64: str
    known_hashes: list[str] = Field(default_factory=list)
    ela_threshold: float = 120.0
    duplicate_hamming_max: int = 4


class ForensicResponse(BaseModel):
    exif_ok: bool
    has_camera_model: bool
    has_capture_time: bool
    ela_variance: float
    ela_suspicious: bool
    perceptual_hash: str
    duplicate_of: str | None
    hamming_distance: int | None
    verdict: str
    flags: list[str]


# --- Yield projection -------------------------------------------------------
class YieldRequest(BaseModel):
    land_id: str = ""
    green_dark: int = 0
    red_blush: int = 0
    red_full: int = 0
    trees_per_bed: int
    sampled_trees: int
    horizon_days: int = 14
    gdd_per_day: float = 12.0
    green_dark_g: float = 12.0
    red_blush_g: float = 14.0
    red_full_g: float = 15.0


class YieldResponse(BaseModel):
    total_trees: int
    sampled_trees: int
    scale_factor: float
    day_mass_kg: list[float]
    cumulative_kg: list[float]
    projected_14d_kg: float
    confidence_pct: float
    per_class_now: dict[str, float]
    notes: list[str]


# --- Prescriptive protocols -------------------------------------------------
class ProtocolRequest(BaseModel):
    verdict_key: str
    severity_pct: float = 0.0
    confidence: str = "MEDIUM"


class ProtocolResponse(BaseModel):
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
    steps: list[str]


# --- Physical-evidence trace validation -------------------------------------
class TraceSample(BaseModel):
    t: float
    gx: float
    gy: float
    gz: float
    ax: float = 0.0
    ay: float = 0.0
    az: float = 0.0


class TraceRequest(BaseModel):
    samples: list[TraceSample]
    target_s: float = 180.0
    duration_tol_s: float = 20.0
    min_motion_variance: float = 1e-4


class TraceResponse(BaseModel):
    valid: bool
    duration_s: float
    samples: int
    motion_variance: float
    score: float
    reasons: list[str]


# --- Model registry / OTA ---------------------------------------------------
class ModelManifestRequest(BaseModel):
    client_versions: dict[str, str] = Field(default_factory=dict)
    base_url: str = ""


class ModelCardOut(BaseModel):
    name: str
    version: str
    sha256: str
    size_bytes: int
    size_mb: float
    quantisation: str
    min_app_version: str
    purpose: str
    input: str
    format: str
    update_available: bool = False
    url: str | None = None


class ModelManifestResponse(BaseModel):
    registry_version: str
    update_count: int
    models: list[ModelCardOut]


# --- Harvest decision engine ------------------------------------------------
class HarvestDecisionRequest(BaseModel):
    accumulated_gdd: float
    current_phase: str = "FRUITING"
    archimedes_sink_ratio: float = 0.0
    curvature_ratio: float = 1.0
    red_pct: float = 0.0
    defect_pct: float = 0.0
    price_trend_14d: float = 0.0


class HarvestDecisionResponse(BaseModel):
    recommended_action: str
    best_window_days: int
    expected_grade: str
    quality_score: float
    spoilage_risk: str
    rationale: list[str]


# --- Protocol rules (field-workflow triggers) -------------------------------
class ProtocolRulesRequest(BaseModel):
    symptoms: list[str] = Field(default_factory=list)
    local_twi: float
    already_recorded_ooze: bool = False


class TriggerOut(BaseModel):
    code: str
    required: bool
    instruction: str
    reason: str
    blocking: bool
    capture_s: int = 0
    steps: list[str] = Field(default_factory=list)


class ProtocolRulesResponse(BaseModel):
    triggers: list[TriggerOut]
    blocked: bool


# --- Economic impact --------------------------------------------------------
class EconomicsRequest(BaseModel):
    area_ha: float
    baseline_yield_t_ha: float = 12.0
    price_per_kg: float = 25000.0
    fertilizer_cost_per_ha_idr: float = 8_000_000.0
    fertilizer_saving_pct: float = 0.25
    yield_uplift_pct: float = 0.10
    outbreak_probability: float = 0.35
    loss_if_late_pct: float = 0.5
    loss_if_early_pct: float = 0.12


class EconomicsResponse(BaseModel):
    area_ha: float
    baseline_revenue_idr: float
    darif_revenue_idr: float
    fertilizer_saving_idr: float
    yield_uplift_idr: float
    avoided_loss_idr: float
    total_benefit_idr: float
    estimated_darif_cost_idr: float
    net_benefit_idr: float
    roi_pct: float
    break_even_fraction: float
    notes: list[str]
