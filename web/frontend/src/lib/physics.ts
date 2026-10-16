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

// --- Prescriptive fertilizer plan (offline mirror of the FastAPI rule engine) ---

export interface FertilizerPlan {
	npk_16_16_16_g_per_l: number;
	kno3_g_per_l: number;
	calcium_g_per_l: number;
	dolomite_g_per_m2: number;
	recommendation: string;
}

/**
 * Chilli fertilization preset (Opsi Topik 2): given soil pH, CEC and plant age,
 * return exact grams of the kocor (drench) mix. Deterministic and matches the
 * server's agronomy/fertilizer.py so on-device output equals the cloud's.
 */
export function prescribeFertilizer(
	ph: number,
	cecMeq: number,
	hst: number,
	targetYieldTHa = 20
): FertilizerPlan {
	const phase = hst < 35 ? 'VEGETATIVE' : hst < 61 ? 'FLOWERING' : 'FRUITING';
	const cecFactor = Math.max(0.6, Math.min(1.6, 15 / Math.max(cecMeq, 1)));
	const yieldFactor = Math.max(0.8, Math.min(1.5, targetYieldTHa / 20));
	const baseNpk = 3.0 * cecFactor * yieldFactor;

	let npk = baseNpk;
	let kno3 = 0;
	let calcium = 0;
	let dolomite = 0;
	const notes: string[] = [];

	if (ph < 5.5) {
		dolomite = 150 * (5.8 - ph);
		notes.push(`pH ${ph.toFixed(1)} masam: dolomit ${dolomite.toFixed(0)} g/m2 2 minggu sebelum pupuk.`);
	}
	if (phase === 'VEGETATIVE') {
		notes.push('Fase vegetatif: prioritaskan N.');
	} else if (phase === 'FLOWERING') {
		npk = baseNpk * 0.6;
		calcium = 2.5;
		kno3 = 1.0;
		notes.push('Pembungaan: wajib Ca+B; HINDARI urea/N tinggi.');
	} else {
		npk = baseNpk * 0.4;
		kno3 = 3.0;
		calcium = 2.0;
		notes.push('Pembuahan: dominan K (KNO3); Ca jaga ketebalan pericarp.');
	}
	return {
		npk_16_16_16_g_per_l: Math.round(npk * 100) / 100,
		kno3_g_per_l: Math.round(kno3 * 100) / 100,
		calcium_g_per_l: Math.round(calcium * 100) / 100,
		dolomite_g_per_m2: Math.round(dolomite * 100) / 100,
		recommendation: notes.join(' ')
	};
}

// --- Immersion gyroscope trace validation (offline mirror of FR-2.3/6.3) ----

export interface TraceSample {
	t: number;
	gx: number;
	gy: number;
	gz: number;
}

export interface TraceVerdict {
	valid: boolean;
	duration_s: number;
	samples: number;
	motion_variance: number;
	score: number;
	reasons: string[];
}

function variance(xs: number[]): number {
	const n = xs.length;
	if (n < 2) return 0;
	const mean = xs.reduce((a, b) => a + b, 0) / n;
	return xs.reduce((s, x) => s + (x - mean) ** 2, 0) / n;
}

/**
 * Validate an immersion sensor trace: duration must be ~180 s and the angular
 * velocity must vary (a perfectly still/constant signal means the timer was
 * skipped). Matches app/services/epidemiology/trace_validation.py.
 */
export function validateTrace(
	samples: TraceSample[],
	targetS = 180,
	durationTolS = 20,
	minMotionVariance = 1e-4
): TraceVerdict {
	const reasons: string[] = [];
	if (samples.length < 5) {
		return { valid: false, duration_s: 0, samples: samples.length, motion_variance: 0, score: 0, reasons: ['trace terlalu pendek (< 5 sampel)'] };
	}
	const times = samples.map((s) => s.t);
	const duration = Math.max(...times) - Math.min(...times);
	if (Math.abs(duration - targetS) > durationTolS) {
		reasons.push(`durasi ${duration.toFixed(0)}s di luar target ${targetS}±${durationTolS}s`);
	}
	const speeds = samples.map((s) => Math.sqrt(s.gx * s.gx + s.gy * s.gy + s.gz * s.gz));
	let varv = variance(speeds);
	const gyro = samples.flatMap((s) => [s.gx, s.gy, s.gz]);
	const isConstant = Math.max(...gyro) - Math.min(...gyro) <= 1e-9;
	if (isConstant) {
		reasons.push('sinyal giroskop konstan (indikasi lewatkan timer)');
		varv = 0;
	}
	if (varv < minMotionVariance) {
		reasons.push(`variasi gerak ${varv.toExponential(1)} < ambang ${minMotionVariance.toExponential(1)}`);
	}
	const durScore = Math.max(0, 1 - Math.abs(duration - targetS) / targetS);
	const motionScore = Math.min(1, varv / (minMotionVariance * 10));
	const score = Math.round((0.5 * durScore + 0.5 * motionScore) * 1000) / 1000;
	const valid = reasons.length === 0 && score >= 0.5;
	if (valid) reasons.push('bukti fisik wajar: durasi & gerak sesuai');
	return { valid, duration_s: Math.round(duration * 100) / 100, samples: samples.length, motion_variance: varv, score, reasons };
}
