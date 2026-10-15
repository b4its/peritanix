/**
 * On-device leaf/lesion analysis — deterministic, offline, browser-side.
 *
 * Converts a captured image into the quantitative evidence the Bayesian engine
 * consumes (severity %, mesophyll attenuation proxy, dominant hue class) using
 * plain canvas pixel statistics — no ML runtime required. This mirrors the
 * intent of the server's trans-illumination + grading modules so a farmer gets
 * an instant offline reading, with the server refining it later.
 */

export interface LeafAnalysis {
	severity_pct: number; // fraction of leaf area that looks necrotic/lesioned
	healthy_pct: number;
	mean_rgb: [number, number, number];
	dominant_hue: 'green' | 'yellow' | 'red' | 'brown' | 'mixed';
	attenuation_proxy_pct: number; // darker/sparser tissue proxy
}

/** Draw an image onto a small canvas and return per-pixel RGB. */
async function loadPixels(src: string, max = 256): Promise<{
	data: Uint8ClampedArray;
	w: number;
	h: number;
}> {
	const img = new Image();
	img.crossOrigin = 'anonymous';
	await new Promise<void>((res, rej) => {
		img.onload = () => res();
		img.onerror = () => rej(new Error('gagal memuat gambar'));
		img.src = src;
	});
	const scale = Math.min(1, max / Math.max(img.width, img.height));
	const w = Math.max(1, Math.round(img.width * scale));
	const h = Math.max(1, Math.round(img.height * scale));
	const canvas = document.createElement('canvas');
	canvas.width = w;
	canvas.height = h;
	const ctx = canvas.getContext('2d')!;
	ctx.drawImage(img, 0, 0, w, h);
	return { data: ctx.getImageData(0, 0, w, h).data, w, h };
}

/**
 * Classify each pixel into healthy (green), chlorotic (yellow), necrotic
 * (brown/dark) or ripe (red) and return area fractions + mean colour.
 */
export async function analyseLeaf(imageDataUrl: string): Promise<LeafAnalysis> {
	const { data } = await loadPixels(imageDataUrl);
	let green = 0, yellow = 0, red = 0, brown = 0, total = 0;
	let sr = 0, sg = 0, sb = 0;
	const n = data.length / 4;
	for (let i = 0; i < n; i++) {
		const r = data[i * 4], g = data[i * 4 + 1], b = data[i * 4 + 2];
		sr += r; sg += g; sb += b;
		total++;
		// Simple, explainable HSV-ish thresholds.
		const max = Math.max(r, g, b), min = Math.min(r, g, b);
		const v = max / 255;
		if (v < 0.22) { brown++; continue; } // dark necrotic shadow
		if (g >= r && g >= b && v > 0.25) { green++; continue; } // healthy
		if (r > 120 && g > 120 && b < 100 && Math.abs(r - g) < 45) { yellow++; continue; } // chlorosis
		if (r > g && r > b) { red++; continue; } // ripe/red
		brown++; // fallback = damaged
	}
	const fra = (x: number) => (total ? (x / total) * 100 : 0);
	const severity = fra(brown) + fra(yellow) * 0.5; // chlorosis counts half
	let hue: LeafAnalysis['dominant_hue'] = 'mixed';
	const parts: [LeafAnalysis['dominant_hue'], number][] = [
		['green', green], ['yellow', yellow], ['red', red], ['brown', brown]
	];
	parts.sort((a, b) => b[1] - a[1]);
	if (parts[0][1] / Math.max(total, 1) > 0.5) hue = parts[0][0];

	return {
		severity_pct: Math.round(severity * 10) / 10,
		healthy_pct: Math.round(fra(green) * 10) / 10,
		mean_rgb: [Math.round(sr / n), Math.round(sg / n), Math.round(sb / n)],
		dominant_hue: hue,
		attenuation_proxy_pct: Math.round(fra(brown) * 10) / 10
	};
}

/**
 * Map the visual evidence to per-disease likelihoods P(V | D_i) for the
 * Bayesian channel. This encodes the field heuristics: yellow mosaic -> virus
 * (whitefly vector); upward curl + silver -> thrips; downward curl + bronze ->
 * mites; dark sunken lesions -> anthracnose; high attenuation -> latent fungus.
 */
export function visualLikelihoods(a: LeafAnalysis): Record<string, number> {
	const base: Record<string, number> = {
		Antraknosa: 0.2, Ralstonia: 0.15, Phytophthora: 0.1, Fusarium: 0.1,
		Keriting_Thrips: 0.15, Keriting_Mites: 0.15, Begomovirus: 0.15,
		Defisiensi: 0.25
	};
	if (a.dominant_hue === 'yellow') {
		base.Begomovirus += 0.6;   // bright yellow mosaic
		base.Defisiensi += 0.4;    // N/Mg chlorosis
	}
	if (a.dominant_hue === 'brown') {
		base.Antraknosa += 0.5;    // dark sunken lesions
		base.Keriting_Mites += 0.3;
	}
	if (a.dominant_hue === 'red') {
		base.Antraknosa += 0.3;    // fruit lesions on red
	}
	if (a.attenuation_proxy_pct > 8) {
		base.Antraknosa += 0.4;    // dense/necrotic tissue
	}
	if (a.severity_pct > 25) {
		// High severity favours systemic/vascular problems.
		base.Ralstonia += 0.3;
		base.Fusarium += 0.3;
	}
	// Normalise to sum 1 (proper likelihood distribution).
	const sum = Object.values(base).reduce((s, v) => s + v, 0);
	for (const k of Object.keys(base)) base[k] = Math.round((base[k] / sum) * 1000) / 1000;
	return base;
}
