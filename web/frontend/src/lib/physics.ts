/**
 * DAPE physics mirror — deterministic agro-physics running in the browser.
 *
 * Mirrors the FastAPI engine (app/services/physics) so the PWA can compute
 * offline. Kept numerically identical; the same formulas are also mirrored in
 * Kotlin (core/physics) for the mobile app.
 */

export const T_BASE_C = 15.0;
export const TARGET_CALORIMETRY_C = 52.0;
export const BOIL_LAPSE_PER_M = 0.0033;

/** Local boiling point of water (C) at elevation (m). */
export function boilingPoint(elevationM: number): number {
	return 100.0 - BOIL_LAPSE_PER_M * elevationM;
}

export interface HotWaterRatio {
	boilingPointC: number;
	targetC: number;
	wellTempC: number;
	vBoilParts: number;
	vWellParts: number;
	ratioLabel: string;
}

/**
 * Black's Law mixing ratio: V_boil*(T_boil - T_target) = V_well*(T_target - T_well)
 * Returns parts normalised so the well-water part equals `scale`.
 */
export function hotWaterRatio(
	elevationM: number,
	wellTempC: number,
	targetC = TARGET_CALORIMETRY_C,
	scale = 9
): HotWaterRatio {
	const tBoil = boilingPoint(elevationM);
	if (!(wellTempC < targetC && targetC < tBoil)) {
		throw new Error(
			`Target tak tercapai: perlu T_sumur < target < T_didih (${wellTempC} < ${targetC} < ${tBoil.toFixed(2)})`
		);
	}
	const vBoil = (scale * (targetC - wellTempC)) / (tBoil - targetC);
	return {
		boilingPointC: tBoil,
		targetC,
		wellTempC,
		vBoilParts: vBoil,
		vWellParts: scale,
		ratioLabel: `${vBoil.toFixed(1)} gayung didih : ${scale} gayung sumur`
	};
}

/** Forward model: temperature of a given mix (for verification). */
export function mixTemperature(
	elevationM: number,
	vBoil: number,
	wellTempC: number,
	vWell: number
): number {
	const tBoil = boilingPoint(elevationM);
	if (vBoil + vWell <= 0) throw new Error('volume harus > 0');
	return (vBoil * tBoil + vWell * wellTempC) / (vBoil + vWell);
}

/** TWI = ln(a / tan(beta)); beta in radians. Floor slope at 0.1 deg. */
export function twi(upslopeArea: number, slopeBetaRad: number): number {
	if (upslopeArea <= 0) throw new Error('upslope area harus > 0');
	const beta = Math.max(Math.abs(slopeBetaRad), (0.1 * Math.PI) / 180);
	return Math.log(upslopeArea / Math.tan(beta));
}

export function drainageRiskClass(t: number): string {
	if (t >= 10) return 'CRITICAL';
	if (t >= 8) return 'HIGH';
	if (t >= 6) return 'MODERATE';
	return 'LOW';
}

export function requiredBedHeightCm(t: number): number {
	if (t >= 10) return 50;
	if (t >= 8) return 40;
	if (t >= 6) return 30;
	return 20;
}

/** GDD_day = max(0, (Tmax+Tmin)/2 - Tbase). */
export function gddDay(tMin: number, tMax: number, tBase = T_BASE_C): number {
	if (tMax < tMin) throw new Error('t_max harus >= t_min');
	return Math.max(0, (tMax + tMin) / 2 - tBase);
}

export function phaseForGdd(
	gddCum: number,
	floweringStart = 350,
	fruitingStart = 700,
	harvestStart = 1050
): string {
	if (gddCum >= harvestStart) return 'HARVEST';
	if (gddCum >= fruitingStart) return 'FRUITING';
	if (gddCum >= floweringStart) return 'FLOWERING';
	return 'VEGETATIVE';
}

/** Archimedes weighted sink ratio (neutrals count 0.5). */
export function archimedesSinkRatio(
	sink: number,
	neutral: number,
	float: number,
	neutralWeight = 0.5
): number {
	const total = sink + neutral + float;
	if (total <= 0) throw new Error('tidak ada buah dihitung');
	return (sink + neutralWeight * neutral) / total;
}

export function gradeFromSinkRatio(r: number): string {
	if (r >= 0.8) return 'A+';
	if (r >= 0.65) return 'A';
	if (r >= 0.45) return 'B';
	return 'BS';
}

// --- Microclimate fungal-blast EWS (offline mirror) ------------------------

function rampUp(x: number, lo: number, hi: number): number {
	if (hi <= lo) throw new Error('hi must exceed lo');
	return Math.min(1, Math.max(0, (x - lo) / (hi - lo)));
}
function rampDown(x: number, lo: number, hi: number): number {
	return 1 - rampUp(x, lo, hi);
}

/** Fungal-blast risk for one hour in [0,1] (matches the FastAPI engine). */
export function hourlyBlastRisk(rh: number, t: number): number {
	const rhM = rampUp(rh, 80, 90);
	const tM = rampUp(t, 15, 27) * rampDown(t, 33, 38);
	return rhM * tM;
}

export interface EwsMirrorResult {
	blast_risk_72h: number;
	peak_risk: number;
	peak_hour_offset: number;
	mean_rh: number;
	mean_t: number;
	action: string;
	irrigation_minutes_per_bed: number;
}

export function microclimateRisk(
	rhSeries: number[],
	tSeries: number[],
	horizonH = 72
): EwsMirrorResult {
	if (rhSeries.length !== tSeries.length || rhSeries.length < 3) {
		throw new Error('rh and t series must be equal length and >= 3 samples');
	}
	const risks = rhSeries.map((rh, i) => hourlyBlastRisk(rh, tSeries[i]));
	const peak0 = risks[risks.length - 1];

	let peak = peak0;
	let peakOff = 0;
	const rhLast = rhSeries[rhSeries.length - 1];
	const tLast = tSeries[tSeries.length - 1];
	for (let h = 1; h <= horizonH; h++) {
		// No-trend offline fallback: hold the last observation.
		const r = hourlyBlastRisk(rhLast, tLast);
		if (r > peak) {
			peak = r;
			peakOff = h;
		}
	}
	const mean_rh = rhSeries.reduce((a, b) => a + b, 0) / rhSeries.length;
	const mean_t = tSeries.reduce((a, b) => a + b, 0) / tSeries.length;

	let action: string;
	let minutes: number;
	if (peak >= 0.7) {
		action = 'RISIKO TINGGI ledakan Antraknosa 72 jam. Fungisida protektif (Mankozeb) sore ini, kurangi penyiraman.';
		minutes = 0;
	} else if (peak >= 0.4) {
		action = 'Risiko sedang. Pantau kelembaban tajuk, hindari penyiraman berlebih.';
		minutes = 10;
	} else {
		action = 'Risiko rendah. Penyiraman normal.';
		minutes = 20;
	}
	return {
		blast_risk_72h: peak0,
		peak_risk: peak,
		peak_hour_offset: peakOff,
		mean_rh,
		mean_t,
		action,
		irrigation_minutes_per_bed: minutes
	};
}
