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
	const res = await fetch(`${API_BASE}${path}`, {
		headers: { 'Content-Type': 'application/json' },
		...init
	});
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

export async function landGeoJSON(): Promise<GeoJSON.FeatureCollection> {
	return req('/api/v1/lands/geojson');
}

export async function landDiseases(id: string): Promise<DiseaseLog[]> {
	const r = await req<{ diseases: DiseaseLog[] }>(`/api/v1/lands/${id}/diseases`);
	return r.diseases ?? [];
}

export async function diseaseGeoJSON(id: string): Promise<GeoJSON.FeatureCollection> {
	return req(`/api/v1/lands/${id}/diseases/geojson`);
}

export async function soilList(id: string): Promise<SoilAssessment[]> {
	const r = await req<{ assessments: SoilAssessment[] }>(`/api/v1/lands/${id}/soil`);
	return r.assessments ?? [];
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
