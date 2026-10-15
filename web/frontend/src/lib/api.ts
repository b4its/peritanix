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
