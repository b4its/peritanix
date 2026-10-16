/**
 * DAPE API client — talks to the Go backend, with offline-first fallback.
 */
import { env } from '$env/dynamic/public';

export const API_BASE =
	env.PUBLIC_API_BASE_URL || 'http://localhost:58080';

export interface Land {
	id: string;
	user_id: string;
	name: string;
	area_ha: number;
	elevation_m: number;
	accumulated_gdd: number;
	current_phase: string;
}

export interface SoilAssessment {
	id: string;
	land_id: string;
	sand_pct: number;
	silt_pct: number;
	clay_pct: number;
	soil_class: string;
	cec_meq: number;
	ph_deterministic: number;
}

export interface DiseaseLog {
	id: string;
	land_id: string;
	lon: number;
	lat: number;
	confirmed_disease: string;
	severity_pct: number;
	confidence_level: string;
	protocol_code: string;
	local_twi: number;
}

async function req<T>(path: string, init?: RequestInit): Promise<T> {
	const headers: Record<string, string> = { 'Content-Type': 'application/json' };
	// Attach the JWT when the user is signed in (browser only).
	if (typeof localStorage !== 'undefined') {
		const token = localStorage.getItem('dape.token');
		if (token) headers['Authorization'] = `Bearer ${token}`;
	}
	const res = await fetch(`${API_BASE}${path}`, { headers, ...init });
	if (!res.ok) {
		const body = await res.text().catch(() => '');
		throw new Error(`${res.status} ${res.statusText}: ${body}`);
	}
	return (await res.json()) as T;
}

export async function health(): Promise<{ status: string; ml: boolean }> {
	return req('/api/v1/health');
}

export async function listLands(): Promise<Land[]> {
	const r = await req<{ lands: Land[] }>('/api/v1/lands');
	return r.lands ?? [];
}

export async function getLand(id: string): Promise<Land> {
	return req(`/api/v1/lands/${id}`);
}

export interface LandInitResult {
	land: Land;
	twi_cache: { cells?: any[]; max_risk?: string; cache_kb?: number } | null;
	cache_kb?: number;
}

/** Create a plot, register the key, and download the TWI/flow cache. */
export async function initLand(input: {
	user_id: string;
	name: string;
	boundary_wkt: string;
	elevation_m?: number;
	planting_date?: string;
	ed25519_public_key?: string;
}): Promise<LandInitResult> {
	return req('/api/v1/lands/init', { method: 'POST', body: JSON.stringify(input) });
}

export async function landGeoJSON(): Promise<GeoJSON.FeatureCollection> {
	return req('/api/v1/lands/geojson');
}

export async function landDiseases(id: string): Promise<DiseaseLog[]> {
	const r = await req<{ diseases: DiseaseLog[] }>(`/api/v1/lands/${id}/diseases`);
	return r.diseases ?? [];
}

export interface DiagnosisResult {
	diagnosis: {
		id: string;
		confirmed_disease: string;
		severity_pct: number;
		confidence_level: string;
		protocol_code: string;
	};
	bayesian: {
		posterior_probabilities: Record<string, number>;
		final_verdict: string;
		confidence_level: string;
		recommended_protocol_code: string;
		physics_metrics?: Record<string, unknown>;
	};
	spatial_context?: Record<string, unknown>;
}

/** Submit a multi-modal diagnostic evidence package for Bayesian fusion. */
export async function evaluateDiagnosis(input: {
	land_id: string;
	lon: number;
	lat: number;
	local_twi?: number;
	visual: Record<string, number>;
	spatial?: Record<string, number>;
	trans_illumination_attenuation_pct?: number;
	severity_pct?: number;
	prevailing_wind_deg?: number;
}): Promise<DiagnosisResult> {
	return req('/api/v1/diagnostics/evaluate', {
		method: 'POST',
		body: JSON.stringify(input)
	});
}

export interface ProtocolTrigger {
	code: string;
	required: boolean;
	instruction: string;
	reason: string;
	blocking: boolean;
	capture_s: number;
	steps: string[];
}

/** Evaluate field-workflow triggers (e.g. mandatory ooze test on wilting+wet). */
export async function protocolRules(input: {
	symptoms: string[];
	local_twi: number;
	already_recorded_ooze?: boolean;
}): Promise<{ triggers: ProtocolTrigger[]; blocked: boolean }> {
	return req('/api/v1/protocols/rules', { method: 'POST', body: JSON.stringify(input) });
}

export async function diseaseGeoJSON(id: string): Promise<GeoJSON.FeatureCollection> {
	return req(`/api/v1/lands/${id}/diseases/geojson`);
}

export async function soilList(id: string): Promise<SoilAssessment[]> {
	const r = await req<{ assessments: SoilAssessment[] }>(`/api/v1/lands/${id}/soil`);
	return r.assessments ?? [];
}

/** Soil-fertility FeatureCollection for the dashboard fertility map layer. */
export async function soilFertilityGeoJSON(): Promise<GeoJSON.FeatureCollection> {
	return req('/api/v1/soil/fertility/geojson');
}

export interface GDDPoint {
	day: string;
	t_min: number;
	t_max: number;
	gdd_day: number;
	gdd_cum: number;
}

export interface TwiCell {
	lon: number;
	lat: number;
	elevation_m: number;
	upslope_area: number;
	twi: number;
	risk: string;
	bed_height_cm: number;
}

export interface TwiGrid {
	land_id: string;
	grid_rows: number;
	grid_cols: number;
	cells: TwiCell[];
	mean_twi: number;
	max_twi: number;
	max_risk: string;
	cache_kb: number;
}

/** TWI grid for a land (drainage-trough contour). Public read. */
export async function landTWI(id: string): Promise<TwiGrid> {
	return req(`/api/v1/lands/${id}/twi`);
}

export interface FertilizerGuardResult {
	land_id: string;
	phase: string;
	plant_gdd: number;
	fortification: boolean;
	blocked: boolean;
	allowed: boolean;
	reason?: string;
	required_protocol?: string;
	guidance?: string;
}

/**
 * Fortification Lock guard: call BEFORE persisting a fertilization log. During
 * peak anthesis (FORTIFICATION_LOCKED) high-nitrogen inputs are BLOCKED.
 */
export async function fertilizerGuard(input: {
	land_id: string;
	product: string;
	npk_ratio?: string;
	nitrogen_pct?: number;
	applied_g?: number;
}): Promise<FertilizerGuardResult> {
	return req('/api/v1/fertilizer/guard', { method: 'POST', body: JSON.stringify(input) });
}

export interface FertilizerPlan {
	npk_16_16_16_g_per_l: number;
	kno3_g_per_l: number;
	calcium_g_per_l: number;
	recommendation: string;
	dolomite_g_per_m2?: number;
}

/** Immersion gyroscope trace sample (anti-cheat evidence). */
export interface TracePoint {
	t: number;
	gx: number;
	gy: number;
	gz: number;
}

export interface TraceResult {
	valid: boolean;
	duration_s: number;
	samples: number;
	motion_variance: number;
	score: number;
	reasons: string[];
}

/** Validate an immersion sensor trace server-side (anti-cheat, FR-6.3). */
export async function validateTrace(
	samples: TracePoint[],
	target_s = 180
): Promise<TraceResult> {
	return req('/api/v1/certificates/validate-trace', {
		method: 'POST',
		body: JSON.stringify({ samples, target_s })
	});
}

/** Prescriptive fertilizer calculator (exact kocor gram doses). */
export async function fertilizerPlan(input: {
	ph: number;
	cec_meq: number;
	hst: number;
	target_yield_t_ha?: number;
	soil_class?: string;
}): Promise<FertilizerPlan> {
	return req('/api/v1/fertilizer/plan', { method: 'POST', body: JSON.stringify(input) });
}

/** GDD daily time series for the accumulation chart. */
export async function gddHistory(
	id: string,
	days = 30
): Promise<{ land_id: string; series: GDDPoint[] }> {
	return req(`/api/v1/lands/${id}/gdd?days=${days}`);
}

export async function recomputeGDD(id: string): Promise<any> {
	return req(`/api/v1/lands/${id}/recompute-gdd`, { method: 'POST', body: '{}' });
}

export interface VerifyResult {
	valid: boolean;
	reason?: string;
	payload?: Record<string, unknown>;
	public_key?: string;
}

/** Verify a scanned certificate. Works without a synced farmer. */
export async function verifyCertificate(cert: {
	payload: Record<string, unknown>;
	signature: string;
	public_key: string;
}): Promise<VerifyResult> {
	return req('/api/v1/certificates/verify', {
		method: 'POST',
		body: JSON.stringify(cert)
	});
}

export interface CalorimetryResult {
	boiling_point_c: number;
	target_c: number;
	v_boil_parts: number;
	v_well_parts: number;
	ratio_label: string;
	tts_text: string;
	countdown_s: number;
}

export async function calorimetry(elevation_m: number, well_temp_c = 27): Promise<CalorimetryResult> {
	return req('/api/v1/postharvest/calorimetry', {
		method: 'POST',
		body: JSON.stringify({ elevation_m, well_temp_c })
	});
}

/** Push an offline delta-sync batch. */
export async function syncDelta(device_id: string, operations: unknown[]): Promise<any> {
	return req('/api/v1/sync/delta', {
		method: 'POST',
		body: JSON.stringify({ device_id, operations })
	});
}

export interface EwsResult {
	blast_risk_72h: number;
	peak_risk: number;
	peak_hour_offset: number;
	mean_rh: number;
	mean_t: number;
	action: string;
	irrigation_minutes_per_bed: number;
}

/** Microclimate fungal-blast early warning (offline fallback in physics.ts). */
export async function ewsMicroclimate(
	land_id: string,
	rh_series: number[],
	t_series: number[]
): Promise<EwsResult> {
	return req('/api/v1/agronomy/ews', {
		method: 'POST',
		body: JSON.stringify({ land_id, rh_series, t_series })
	});
}

/** EWS computed from the land's stored sensor readings. */
export async function landEws(land_id: string): Promise<EwsResult & { insufficient_data?: boolean }> {
	return req(`/api/v1/lands/${land_id}/ews`);
}

export interface ClimateReading {
	observed_at: string;
	rh: number;
	temp_c: number;
	soil_moist?: number;
}

/** Ingest a batch of sensor readings (ESP32/BLE logger pulled by the phone). */
export async function climateIngest(
	land_id: string,
	readings: ClimateReading[],
	device_id = 'web'
): Promise<{ ingested: number; ews?: EwsResult }> {
	return req('/api/v1/climate/ingest', {
		method: 'POST',
		body: JSON.stringify({ land_id, device_id, readings })
	});
}

export interface GradeResult {
	curvature_ratio: number;
	red_pct: number;
	defect_pct: number;
	grade: string;
	reasons: string[];
}

/** Grade a harvested fruit batch. */
export async function gradeFruit(
	land_id: string,
	contour: [number, number][],
	mean_a_channel: number,
	lesion_area: number,
	total_area: number
): Promise<GradeResult> {
	return req('/api/v1/postharvest/grade', {
		method: 'POST',
		body: JSON.stringify({ land_id, contour, mean_a_channel, lesion_area, total_area })
	});
}

/** Fortification compliance is derived from a land's current phase. */
export function fortificationCompliance(lands: Land[]): {
	compliant: Land[];
	locked: Land[];
	total: number;
} {
	const locked = lands.filter((l) => l.current_phase === 'FORTIFICATION_LOCKED');
	return { compliant: locked, locked, total: lands.length };
}

export interface YieldResult {
	total_trees: number;
	sampled_trees: number;
	scale_factor: number;
	day_mass_kg: number[];
	cumulative_kg: number[];
	projected_14d_kg: number;
	confidence_pct: number;
	per_class_now: Record<string, number>;
	notes: string[];
}

/** Estimate harvestable tonnage over a horizon (default 14 days). */
export async function yieldProjection(input: {
	land_id?: string;
	green_dark: number;
	red_blush: number;
	red_full: number;
	trees_per_bed: number;
	sampled_trees: number;
	horizon_days?: number;
	gdd_per_day?: number;
}): Promise<YieldResult> {
	return req('/api/v1/yield/projection', {
		method: 'POST',
		body: JSON.stringify(input)
	});
}

export interface FruitCountResult {
	green_dark: number;
	red_blush: number;
	red_full: number;
	total: number;
	mean_blob_area_px: Record<string, number>;
	image_size: [number, number];
	method: string;
}

/**
 * Auto-count fruit by ripeness class from a canopy photo (Opsi 3). The counts
 * feed `yieldProjection` directly, replacing manual tallying.
 */
export async function fruitCount(input: {
	land_id?: string;
	image: number[][][];
	min_area_px?: number;
	max_area_px?: number;
}): Promise<FruitCountResult> {
	return req('/api/v1/yield/fruit-count', {
		method: 'POST',
		body: JSON.stringify(input)
	});
}

export interface Protocol {
	code: string;
	disease: string;
	active_ingredient: string;
	dose_g_per_l: number;
	unit: string;
	interval_days: number;
	window: string;
	safety: string;
	organic_fallback: string;
	severity_pct: number;
	action_line: string;
	steps: string[];
}

/** Get an actionable prescription for a verdict. */
export async function prescribeProtocol(
	verdict_key: string,
	severity_pct = 0
): Promise<Protocol> {
	return req('/api/v1/protocols/prescribe', {
		method: 'POST',
		body: JSON.stringify({ verdict_key, severity_pct })
	});
}

export async function protocolCatalog(): Promise<{ protocols: Protocol[] }> {
	return req('/api/v1/protocols');
}

export interface ArchimedesResult {
	sink_ratio: number;
	physics_grade: string;
	brine_relative_density: number | null;
}

export async function archimedes(input: {
	sink_count: number;
	neutral_count: number;
	float_count: number;
	salt_g?: number;
	water_ml?: number;
}): Promise<ArchimedesResult> {
	return req('/api/v1/postharvest/archimedes', { method: 'POST', body: JSON.stringify(input) });
}

export interface HarvestDecisionResult {
	recommended_action: string;
	best_window_days: number;
	expected_grade: string;
	quality_score: number;
	spoilage_risk: string;
	rationale: string[];
}

export async function harvestDecision(input: {
	accumulated_gdd: number;
	current_phase?: string;
	archimedes_sink_ratio?: number;
	curvature_ratio?: number;
	red_pct?: number;
	defect_pct?: number;
	price_trend_14d?: number;
}): Promise<HarvestDecisionResult> {
	return req('/api/v1/postharvest/decision', { method: 'POST', body: JSON.stringify(input) });
}

export interface ModelCard {
	name: string;
	version: string;
	sha256: string;
	size_bytes: number;
	size_mb: number;
	quantisation: string;
	purpose: string;
	update_available?: boolean;
	url?: string;
}

export interface ModelRuntime {
	onnxruntime_available: boolean;
	models_dir: string;
	models: { name: string; version: string; loadable: boolean; fallback: string | null }[];
}

/** Fetch the OTA model manifest for a client's current version set. */
export async function modelManifest(
	clientVersions: Record<string, string> = {}
): Promise<{ registry_version: string; update_count: number; models: ModelCard[] }> {
	return req('/api/v1/models/manifest', {
		method: 'POST',
		body: JSON.stringify({ client_versions: clientVersions })
	});
}

/** List registered models (public). */
export async function modelList(): Promise<{ models: ModelCard[] }> {
	return req('/api/v1/models');
}

export interface HarvestBatch {
	id: string;
	land_id: string;
	batch_code: string;
	boiling_point_calc: number;
	hot_water_ratio: string;
	archimedes_sink_ratio: number;
	grade_result: string;
	crypto_signature?: string;
	signer_public_key?: string;
}

/** Persist a signed harvest batch so buyers/off-takers can look it up later. */
export async function saveHarvestBatch(batch: {
	land_id: string;
	batch_code: string;
	boiling_point_calc: number;
	hot_water_ratio: string;
	archimedes_sink_ratio: number;
	grade_result: string;
	payload_hash?: string;
	crypto_signature?: string;
	signer_public_key?: string;
}): Promise<HarvestBatch> {
	return req('/api/v1/harvest/batches', { method: 'POST', body: JSON.stringify(batch) });
}

/** Look up a stored harvest batch by its code. */
export async function getHarvestBatch(code: string): Promise<HarvestBatch> {
	return req(`/api/v1/harvest/batches/${encodeURIComponent(code)}`);
}

export interface EconomicsResult {
	area_ha: number;
	baseline_revenue_idr: number;
	darif_revenue_idr: number;
	fertilizer_saving_idr: number;
	yield_uplift_idr: number;
	avoided_loss_idr: number;
	total_benefit_idr: number;
	estimated_darif_cost_idr: number;
	net_benefit_idr: number;
	roi_pct: number;
	break_even_fraction: number;
	notes: string[];
}

/** Farm-level economic impact (fertilizer saving + avoided loss + uplift). */
export async function economics(input: {
	area_ha: number;
	baseline_yield_t_ha?: number;
	price_per_kg?: number;
	fertilizer_cost_per_ha_idr?: number;
	fertilizer_saving_pct?: number;
	yield_uplift_pct?: number;
	outbreak_probability?: number;
	loss_if_late_pct?: number;
	loss_if_early_pct?: number;
}): Promise<EconomicsResult> {
	return req('/api/v1/economics', { method: 'POST', body: JSON.stringify(input) });
}

export interface AuthResult {
	token: string;
	user: { id: string; name: string; role: string };
}

export async function register(input: {
	name: string;
	phone: string;
	role?: string;
	password: string;
}): Promise<AuthResult> {
	return req('/api/v1/auth/register', { method: 'POST', body: JSON.stringify(input) });
}

export async function login(phone: string, password: string): Promise<AuthResult> {
	return req('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ phone, password }) });
}
