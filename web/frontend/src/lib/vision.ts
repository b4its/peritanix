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

export interface CurlOptions {
	curlDirection?: 'up' | 'down' | 'none';
	undersideColor?: 'silvery' | 'bronze' | 'normal';
	mosaicVeins?: boolean;
	sunkenLesions?: boolean;
}

export interface CurlDiagnosis {
	condition: 'Keriting_Thrips' | 'Keriting_Mites' | 'Begomovirus' | 'Antraknosa' | 'Defisiensi' | 'Sehat';
	title: string;
	description: string;
	active_ingredient: string;
	dosage_per_liter: string;
	spraying_instructions: string;
	organic_alternative: string;
	confidence: number;
	symptoms: string[];
}

/**
 * On-device differential diagnosis for chili leaf curling & lesions (Opsi 1).
 * Distinguishes Thrips (upward/silvery) vs Mites (downward/bronze) vs
 * Begomovirus (yellow mosaic/veins) vs Anthracnose (sunken concentric spots).
 */
export function differentiateLeafCurl(
	analysis: LeafAnalysis,
	options: CurlOptions = {}
): CurlDiagnosis {
	const dir = options.curlDirection ?? 'none';
	const under = options.undersideColor ?? 'normal';
	const mosaic = options.mosaicVeins ?? (analysis.dominant_hue === 'yellow');
	const sunken = options.sunkenLesions ?? (analysis.dominant_hue === 'brown' && analysis.attenuation_proxy_pct > 8);

	// Thrips: upward curling, silvery underside
	if (dir === 'up' || under === 'silvery' || (analysis.dominant_hue === 'green' && analysis.mean_rgb[0] > 135 && analysis.severity_pct > 5 && dir !== 'down')) {
		return {
			condition: 'Keriting_Thrips',
			title: 'Keriting Daun akibat Thrips (Thrips parvispinus)',
			description: 'Daun mengeriting ke atas menyerupai perahu/mangkuk, permukaan bawah keperakan mengkilap akibat sel epidermis dihisap cairan selnya.',
			active_ingredient: 'Abamektin 18 g/L atau Spinetoram 120 g/L',
			dosage_per_liter: '0.25 – 0.5 mL per liter air',
			spraying_instructions: 'Semprot pagi hari (06:00–08:30) saat thrips aktif di pucuk muda dan ketiak daun. Wajib membasahi permukaan bawah daun.',
			organic_alternative: 'Ekstrak daun mimba (Azadirachtin 0.3%) + tembakau, atau pelepasan predator alami Orius sp.',
			confidence: dir === 'up' && under === 'silvery' ? 0.95 : 0.82,
			symptoms: [
				'Helaian daun melengkung ke atas (boat/canoe shape)',
				'Permukaan bawah daun tampak keperakan atau bercak garis perak',
				'Pucuk daun mengecil, kaku, dan pertumbuhan tunas terhambat'
			]
		};
	}

	// Mites: downward curling, spoon-shaped, bronze coloration
	if (dir === 'down' || under === 'bronze' || (analysis.dominant_hue === 'brown' && analysis.severity_pct > 8 && !sunken)) {
		return {
			condition: 'Keriting_Mites',
			title: 'Keriting Daun akibat Tungau / Mites (Polyphagotarsonemus latus)',
			description: 'Daun mengeriting ke bawah melengkung cembung seperti sendok terbalik, tekstur daun kaku tebal dan rapuh, bagian bawah berwarna kecokelatan/tembaga.',
			active_ingredient: 'Akarisida (Bifenazat 240 g/L atau Piridaben 135 g/L / Abamektin)',
			dosage_per_liter: '0.5 – 1.0 mL per liter air',
			spraying_instructions: 'Semprot sore hari (16:00–18:00) dengan tekanan nozzle halus berkabut ke balik daun muda. Wajib rotasi golongan kimia tiap 2 aplikasi.',
			organic_alternative: 'Belerang mikronize (Sulfur 80%) dosis 2 g/L air atau semprot jamur Beauveria bassiana.',
			confidence: dir === 'down' || under === 'bronze' ? 0.94 : 0.80,
			symptoms: [
				'Tepi daun melengkung ke bawah (sendok terbalik)',
				'Warna permukaan bawah daun berubah kecokelatan tembaga (bronze/russet)',
				'Daun menjadi kaku, tebal, dan mudah patah saat ditekuk'
			]
		};
	}

	// Begomovirus / Gemini Virus: yellow mosaic, vein clearing
	if (mosaic || analysis.dominant_hue === 'yellow') {
		return {
			condition: 'Begomovirus',
			title: 'Virus Kuning / Gemini Virus (Begomovirus / Pepper Yellow Leaf Curl)',
			description: 'Klorosis kuning cerah menyolok berpola mosaik di antara tulang daun, tulang daun menebal kehijauan, daun mengecil dan berkerut rimbun.',
			active_ingredient: 'Pengendali Vektor Kutu Kebul (Imidakloprid 200 g/L atau Tiametoksam 25 WG)',
			dosage_per_liter: '0.25 – 0.5 g/L air untuk semprot vektor',
			spraying_instructions: 'Virus sistemik tidak bisa disembuhkan kimiawi. Segera cabut dan bakar tanaman bergejala berat. Semprot insektisida vektor pada tanaman sekitarnya.',
			organic_alternative: 'Pasang perangkap kuning berperekat (yellow sticky trap) 40 lembar/ha + gunakan mulsa plastik perak.',
			confidence: mosaic && analysis.dominant_hue === 'yellow' ? 0.96 : 0.85,
			symptoms: [
				'Bercak belang kuning menyolok berpola mosaik',
				'Tulang daun menebal dan jernih (vein clearing/thickening)',
				'Pertumbuhan ruas pucuk memendek membentuk tajuk seperti sapu/kerdil'
			]
		};
	}

	// Antraknosa: sunken dark concentric lesions
	if (sunken || analysis.attenuation_proxy_pct > 10 || (analysis.dominant_hue === 'brown' && analysis.severity_pct > 15)) {
		return {
			condition: 'Antraknosa',
			title: 'Patek / Antraknosa (Colletotrichum capsici / gloeosporioides)',
			description: 'Bercak nekrotik gelap cekung melingkar dengan lingkaran konsentris titik spora hitam/oranye pada buah atau daun.',
			active_ingredient: 'Fungisida Sistemik + Kontak (Mankozeb 80% + Azoksistrobin 250 g/L atau Difenokonazol)',
			dosage_per_liter: '2.0 – 2.5 g per liter air',
			spraying_instructions: 'Semprot sore hari (16:00–18:00) saat cuaca teduh. Petik dan musnahkan semua buah yang memiliki bercak patek sebelum penyemprotan.',
			organic_alternative: 'Kocor Trichoderma harzianum ke perakaran tanah + semprot ekstrak rimpang lengkuas dan kunyit.',
			confidence: sunken ? 0.95 : 0.84,
			symptoms: [
				'Bercak cekung cokelat kehitaman berbentuk bulat konsentris',
				'Permukaan lesi memproduksi massa spora seperti lendir oranye saat basah',
				'Buah layu, keriput, dan gugur prematur'
			]
		};
	}

	// Deficiency or Healthy
	if (analysis.severity_pct > 10) {
		return {
			condition: 'Defisiensi',
			title: 'Defisiensi Unsur Hara Makro/Mikro (Bukan Patogen)',
			description: 'Klorosis atau pucat tanpa nekrosis melingkar dan tanpa deformasi kurvatur daun yang tajam.',
			active_ingredient: 'Pupuk Daun NPK Seimbang + Magnesium Sulfat (MgSO4)',
			dosage_per_liter: '2.0 – 3.0 g per liter air',
			spraying_instructions: 'Semprot foliar pagi hari (07:00–09:00). Periksa pH tanah; jika pH < 5.5 berikan dolomit untuk membuka ikatan hara.',
			organic_alternative: 'Aplikasi pupuk organik cair (POC) kotoran hewan terfermentasi + asam humat.',
			confidence: 0.78,
			symptoms: [
				'Menguning merata dari daun tua ke daun muda (Defisiensi N)',
				'Klorosis di antara tulang daun (Defisiensi Mg)',
				'Tidak ada bekas gigitan kutu atau lendir spora'
			]
		};
	}

	return {
		condition: 'Sehat',
		title: 'Daun Sehat / Tidak Terdeteksi Gejala Serius',
		description: 'Klorofil daun normal, pigmentasi hijau dominan (> 85%), tidak terindikasi kelainan morfologi.',
		active_ingredient: 'Pemeliharaan Rutin (Silika / Asam Amino)',
		dosage_per_liter: '1.0 mL per liter air',
		spraying_instructions: 'Lakukan pemantauan berkala 3 hari sekali.',
		organic_alternative: 'Semprot PGPR (Plant Growth-Promoting Rhizobacteria) untuk ketahanan hayati.',
		confidence: 0.92,
		symptoms: ['Permukaan daun hijau rata', 'Tajuk tumbuh simetris', 'Tidak ada nekrosis']
	};
}
