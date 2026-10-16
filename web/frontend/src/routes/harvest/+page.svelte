<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { browser } from '$app/environment';
	import {
		calorimetry,
		archimedes,
		harvestDecision,
		listLands,
		validateTrace,
		type CalorimetryResult,
		type ArchimedesResult,
		type HarvestDecisionResult,
		type Land,
		type TracePoint,
		type TraceResult
	} from '$lib/api';
	import { speak, stopSpeaking, ttsSupported, dosingSpeech } from '$lib/tts';
	import { validateTrace as validateTraceOffline } from '$lib/physics';

	let lands = $state<Land[]>([]);
	let landId = $state('');
	let elevation = $state(850);
	let wellTemp = $state(27);
	let calo = $state<CalorimetryResult | null>(null);
	let err = $state('');

	// 180s immersion countdown (TAHAP 5.1)
	let countdown = $state(0);
	let ticking = $state(false);
	let timer: ReturnType<typeof setInterval> | null = null;

	// Gyroscope trace (anti-cheat, TAHAP 6.3)
	let recording = $state(false);
	let trace = $state<TracePoint[]>([]);
	let traceResult = $state<TraceResult | null>(null);
	let motionHandler: ((e: DeviceMotionEvent) => void) | null = null;

	function startTrace() {
		if (typeof window === 'undefined' || !window.DeviceMotionEvent) {
			err = 'Sensor gerak tidak tersedia di perangkat ini.';
			return;
		}
		trace = [];
		traceResult = null;
		recording = true;
		const t0 = performance.now();
		motionHandler = (e: DeviceMotionEvent) => {
			const r = (e as any).rotationRate || {};
			trace.push({
				t: (performance.now() - t0) / 1000,
				gx: (r.alpha || 0) * (Math.PI / 180),
				gy: (r.beta || 0) * (Math.PI / 180),
				gz: (r.gamma || 0) * (Math.PI / 180)
			});
		};
		window.addEventListener('devicemotion', motionHandler);
	}

	function stopTrace() {
		recording = false;
		if (motionHandler) {
			window.removeEventListener('devicemotion', motionHandler);
			motionHandler = null;
		}
	}

	async function validateRecordedTrace() {
		stopTrace();
		traceResult = null;
		try {
			traceResult = await validateTrace(trace, 180);
		} catch {
			// Offline fallback: local mirror.
			const v = validateTraceOffline(trace, 180);
			traceResult = {
				valid: v.valid,
				duration_s: v.duration_s,
				samples: v.samples,
				motion_variance: v.motion_variance,
				score: v.score,
				reasons: v.reasons
			};
		}
	}

	function startCountdown() {
		if (!calo) return;
		countdown = calo.countdown_s || 180;
		ticking = true;
		startTrace();
		if (timer) clearInterval(timer);
		timer = setInterval(() => {
			countdown -= 1;
			if (countdown <= 0) {
				clearInterval(timer!);
				timer = null;
				ticking = false;
				stopTrace();
				speak('Waktu perendaman selesai. Angkat dan tiriskan cabai.');
			}
		}, 1000);
	}

	function toggleTTS() {
		if (!calo) return;
		if (ticking) {
			stopSpeaking();
			return;
		}
		speak(dosingSpeech(calo.v_boil_parts, calo.v_well_parts, calo.countdown_s));
	}

	onDestroy(() => {
		if (timer) clearInterval(timer);
		stopTrace();
		stopSpeaking();
	});

	// Archimedes assay inputs
	let sinkCount = $state(16);
	let neutralCount = $state(3);
	let floatCount = $state(1);
	let arch = $state<ArchimedesResult | null>(null);

	// Quality grading inputs
	let redPct = $state(90);
	let defectPct = $state(2);
	let curv = $state(1.05);

	// Market signal
	let priceTrend = $state(0);

	let decision = $state<HarvestDecisionResult | null>(null);
	let gdd = $state(1100);
	let phase = $state('HARVEST');

	onMount(async () => {
		if (!browser) return;
		try {
			lands = await listLands();
			if (lands.length > 0) {
				landId = lands[0].id;
				gdd = lands[0].accumulated_gdd ?? 1100;
				phase = lands[0].current_phase ?? 'HARVEST';
				elevation = lands[0].elevation_m || 850;
			}
		} catch (e: any) {
			err = e.message;
		}
	});

	async function doCalo() {
		err = '';
		try {
			calo = await calorimetry(elevation, wellTemp);
		} catch (e: any) {
			// Offline fallback.
			try {
				const { hotWaterRatio } = await import('$lib/physics');
				const r = hotWaterRatio(elevation, wellTemp);
				calo = {
					boiling_point_c: r.boilingPointC,
					target_c: r.targetC,
					v_boil_parts: r.vBoilParts,
					v_well_parts: r.vWellParts,
					ratio_label: r.ratioLabel,
					tts_text: `Campurkan ${r.vBoilParts.toFixed(1)} gayung air mendidih dengan ${r.vWellParts} gayung air sumur.`,
					countdown_s: 180
				};
				err = '⚠️ Dihitung offline (server tak terjangkau).';
			} catch (e2: any) {
				err = e2.message;
			}
		}
	}

	async function doArch() {
		err = '';
		try {
			arch = await archimedes({ sink_count: sinkCount, neutral_count: neutralCount, float_count: floatCount, salt_g: 150, water_ml: 1000 });
		} catch (e: any) {
			err = e.message;
		}
	}

	async function doDecide() {
		err = '';
		try {
			decision = await harvestDecision({
				accumulated_gdd: gdd,
				current_phase: phase,
				archimedes_sink_ratio: arch?.sink_ratio ?? sinkCount / (sinkCount + neutralCount + floatCount),
				curvature_ratio: curv,
				red_pct: redPct,
				defect_pct: defectPct,
				price_trend_14d: priceTrend
			});
		} catch (e: any) {
			err = e.message;
		}
	}

	function actionClass(a: string): string {
		if (a === 'SELL_NOW') return 'high';
		if (a === 'HOLD_14D') return 'medium';
		return 'low';
	}
</script>

<svelte:head>
	<title>DAPE — Panen & Pasca-Panen</title>
</svelte:head>

<h1>Panen &amp; Pasca-Panen</h1>
<p class="muted">
	Fisika deterministik pasca-panen: sterilisasi 52 °C (Asas Black), uji densitas
	Archimedes, penilaian mutu, dan keputusan waktu jual.
</p>

{#if err}<p class="error">{err}</p>{/if}

<div class="grid" style="margin-top:1rem">
	<div class="card">
		<h2>1. Sterilisasi 52 °C</h2>
		<label class="muted" for="el">Elevasi (m)</label>
		<input id="el" type="number" bind:value={elevation} />
		<label class="muted" for="wt" style="display:block;margin-top:0.5rem">Suhu air sumur (°C)</label>
		<input id="wt" type="number" bind:value={wellTemp} step="0.5" />
		<button class="btn" style="margin-top:0.75rem" onclick={doCalo}>Hitung Takaran</button>
		{#if calo}
			<p style="margin-top:0.75rem">Titik didih: <strong>{calo.boiling_point_c.toFixed(2)} °C</strong></p>
			<p><strong>{calo.ratio_label}</strong></p>
			<p class="muted">{calo.tts_text} ({calo.countdown_s} detik)</p>
			<div style="display:flex;gap:0.5rem;flex-wrap:wrap;margin-top:0.5rem">
				<button class="btn" onclick={toggleTTS} disabled={!ttsSupported()}>
					{ticking ? '⏹ Stop Suara' : '🔊 Bacakan (TTS)'}
				</button>
				<button class="btn" onclick={startCountdown}>⏱ Mulai Perendaman {calo.countdown_s}s</button>
			</div>
			{#if ticking || countdown > 0}
				<p style="margin-top:0.5rem;font-size:1.4rem;font-weight:700">
					⏳ {String(Math.floor(countdown / 60)).padStart(2, '0')}:{String(countdown % 60).padStart(2, '0')}
					{#if !ticking && countdown <= 0}<span class="badge high">SELESAI</span>{/if}
				</p>
			{/if}
			<div style="display:flex;gap:0.5rem;flex-wrap:wrap;margin-top:0.5rem">
				<button class="btn secondary" onclick={recording ? stopTrace : startTrace}>
					{recording ? '⏹ Stop Rekam Gerak' : '📳 Rekam Gerak (Anti-Cheat)'}
				</button>
				<button class="btn secondary" onclick={validateRecordedTrace} disabled={trace.length === 0}>
					✅ Validasi Bukti Fisik ({trace.length} sampel)
				</button>
			</div>
			{#if traceResult}
				<div style="margin-top:0.5rem;border:1px solid {traceResult.valid ? '#86efac' : '#fca5a5'};border-radius:8px;padding:0.5rem">
					<p>
						{traceResult.valid ? '✅ Bukti fisik wajar' : '❌ Bukti fisik tidak wajar'}
						· skor {traceResult.score} · durasi {traceResult.duration_s.toFixed(0)}s
					</p>
					{#each traceResult.reasons as r}<p class="muted">{r}</p>{/each}
				</div>
			{/if}
		{/if}
	</div>

	<div class="card">
		<h2>2. Uji Archimedes</h2>
		<div style="display:flex;gap:0.5rem">
			<div><label class="muted" for="sk">Tenggelam</label><input id="sk" type="number" bind:value={sinkCount} /></div>
			<div><label class="muted" for="nu">Melayang</label><input id="nu" type="number" bind:value={neutralCount} /></div>
			<div><label class="muted" for="fl">Mengapung</label><input id="fl" type="number" bind:value={floatCount} /></div>
		</div>
		<button class="btn" style="margin-top:0.75rem" onclick={doArch}>Hitung Densitas</button>
		{#if arch}
			<p style="margin-top:0.75rem">Sink ratio: <strong>{arch.sink_ratio.toFixed(3)}</strong> →
				<span class="badge high">{arch.physics_grade}</span></p>
		{/if}
	</div>

	<div class="card">
		<h2>3. Mutu Buah</h2>
		<label class="muted" for="rd">Warna merah (%)</label>
		<input id="rd" type="number" bind:value={redPct} />
		<label class="muted" for="df" style="display:block;margin-top:0.5rem">Cacat/patek (%)</label>
		<input id="df" type="number" bind:value={defectPct} />
		<label class="muted" for="cv" style="display:block;margin-top:0.5rem">Kelengkungan (1=lurus)</label>
		<input id="cv" type="number" bind:value={curv} step="0.01" />
	</div>
</div>

<div class="card" style="margin-top:1rem">
	<h2>4. Keputusan Panen &amp; Waktu Jual</h2>
	<div style="display:flex;gap:0.5rem;flex-wrap:wrap;align-items:end">
		<div><label class="muted" for="li">Lahan</label>
			<select id="li" bind:value={landId}>
				{#each lands as l}<option value={l.id}>{l.name}</option>{/each}
			</select>
		</div>
		<div><label class="muted" for="gd">GDD</label><input id="gd" type="number" bind:value={gdd} /></div>
		<div><label class="muted" for="ph">Fase</label><input id="ph" bind:value={phase} /></div>
		<div><label class="muted" for="pt">Tren harga 14 hari (±%)</label><input id="pt" type="number" bind:value={priceTrend} step="0.05" /></div>
		<button class="btn" onclick={doDecide}>🧭 Hitung Keputusan</button>
	</div>

	{#if decision}
		<div style="margin-top:0.75rem">
			<p>
				Rekomendasi:
				<span class="badge {actionClass(decision.recommended_action)}">{decision.recommended_action}</span>
				· Grade <strong>{decision.expected_grade}</strong>
				· Skor mutu <strong>{decision.quality_score.toFixed(2)}</strong>
				· Risiko busuk <span class="badge {actionClass(decision.spoilage_risk === 'HIGH' ? 'SELL_NOW' : 'HOLD_14D')}">{decision.spoilage_risk}</span>
			</p>
			<p class="muted">Jendela jual terbaik: <strong>{decision.best_window_days} hari</strong></p>
			<ul>{#each decision.rationale as r}<li class="muted">{r}</li>{/each}</ul>
		</div>
	{/if}
</div>
