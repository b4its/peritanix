<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import {
		listLands,
		evaluateDiagnosis,
		prescribeProtocol,
		protocolRules,
		type Land,
		type DiagnosisResult,
		type Protocol,
		type ProtocolTrigger
	} from '$lib/api';
	import { analyseLeaf, visualLikelihoods, type LeafAnalysis } from '$lib/vision';

	let lands = $state<Land[]>([]);
	let landId = $state('');
	let lon = $state(107.6035);
	let lat = $state(-6.9035);
	let localTwi = $state(9.0);
	let wind = $state(135);
	let imageUrl = $state('');
	let analysis = $state<LeafAnalysis | null>(null);
	let result = $state<DiagnosisResult | null>(null);
	let protocol = $state<Protocol | null>(null);
	let oozeReq = $state<ProtocolTrigger | null>(null);
	let oozeConfirmed = $state(false);
	let err = $state('');
	let busy = $state(false);
	let analysisSource = $state<'offline' | 'none'>('none');

	onMount(async () => {
		if (!browser) return;
		try {
			lands = await listLands();
			if (lands.length > 0) landId = lands[0].id;
		} catch (e: any) {
			err = e.message;
		}
	});

	async function onFile(e: Event) {
		const input = e.target as HTMLInputElement;
		const file = input.files?.[0];
		if (!file) return;
		imageUrl = URL.createObjectURL(file);
		try {
			analysis = await analyseLeaf(imageUrl);
			analysisSource = 'offline';
			err = '';
		} catch (e: any) {
			err = 'Analisis gambar: ' + e.message;
		}
	}

	async function submit() {
		err = '';
		result = null;
		protocol = null;
		if (!landId) {
			err = 'Pilih lahan dulu.';
			return;
		}
		// Diagnostic protocol rules: a wilting plant in a wet cell mandates the
		// destructive ooze test before a verdict can be trusted.
		try {
			const rules = await protocolRules({ symptoms: ['layu'], local_twi: localTwi });
			if (rules.blocked) {
				oozeReq = rules.triggers[0];
			}
		} catch {
			/* best effort — continue if the rules engine is unreachable */
		}
		if (oozeReq && !oozeConfirmed) {
			// Do not proceed to the diagnosis until the ooze test is acknowledged.
			return;
		}
		// Build the visual evidence: prefer the offline analysis, else a neutral prior.
		const visual = analysis
			? visualLikelihoods(analysis)
			: { Antraknosa: 0.3, Ralstonia: 0.2, Keriting_Thrips: 0.2, Defisiensi: 0.3 };
		busy = true;
		try {
			result = await evaluateDiagnosis({
				land_id: landId,
				lon,
				lat,
				local_twi: localTwi,
				visual,
				trans_illumination_attenuation_pct: analysis?.attenuation_proxy_pct,
				severity_pct: analysis?.severity_pct,
				prevailing_wind_deg: wind
			});
			// Fetch the actionable prescription for the verdict.
			protocol = await prescribeProtocol(
				result.bayesian.recommended_protocol_code,
				result.diagnosis.severity_pct
			);
		} catch (e: any) {
			err = e.message;
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head>
	<title>DAPE — Diagnosis Patologi</title>
</svelte:head>

<h1>Diagnosis Patologi Multi-Modal</h1>
<p class="muted">
	Foto daun/buah → analisis offline di perangkat (severitas &amp; warna) → fusi
	Bayes server (visual + spasial + TWI) → resep tindakan. Bekerja offline untuk
	analisis awal, tersinkron saat ada sinyal.
</p>

{#if err}<p class="error">{err}</p>{/if}

<div class="grid" style="margin-top:1rem">
	<div class="card">
		<h2>1. Ambil Foto</h2>
		<input type="file" accept="image/*" capture="environment" onchange={onFile} />
		{#if imageUrl}
			<img src={imageUrl} alt="sampel" style="width:100%;border-radius:8px;margin-top:0.5rem" />
		{/if}
		{#if analysis}
			<p style="margin-top:0.5rem">
				Severitas: <strong>{analysis.severity_pct}%</strong> ·
				Sehat: {analysis.healthy_pct}% ·
				Warna dominan: <strong>{analysis.dominant_hue}</strong>
			</p>
			<p class="muted">
				Attenuasi (proxy jamur): {analysis.attenuation_proxy_pct}% ·
				RGB {analysis.mean_rgb.join(',')} · <em>dianalisis offline</em>
			</p>
		{/if}
	</div>

	<div class="card">
		<h2>2. Konteks Spasial</h2>
		<label class="muted" for="li">Lahan</label>
		<select id="li" bind:value={landId}>
			{#each lands as l}<option value={l.id}>{l.name}</option>{/each}
		</select>
		<div style="display:flex;gap:0.5rem;margin-top:0.5rem">
			<div><label class="muted" for="lo">Lon</label><input id="lo" type="number" bind:value={lon} step="0.0001" /></div>
			<div><label class="muted" for="la">Lat</label><input id="la" type="number" bind:value={lat} step="0.0001" /></div>
		</div>
		<label class="muted" for="tw" style="display:block;margin-top:0.5rem">TWI lokal</label>
		<input id="tw" type="number" bind:value={localTwi} step="0.1" />
		<label class="muted" for="wd" style="display:block;margin-top:0.5rem">Arah angin (°)</label>
		<input id="wd" type="number" bind:value={wind} />
		<button class="btn" style="margin-top:0.75rem" onclick={submit} disabled={busy}>
			{busy ? 'Mengevaluasi…' : '🧠 Evaluasi Diagnosis'}
		</button>
	</div>
</div>

{#if oozeReq}
	<div class="card" style="margin-top:1rem;border:2px solid #dc2626;background:#fef2f2">
		<h2 style="color:#dc2626">🔒 Uji Ooze Wajib (Destructive Test)</h2>
		<p>{oozeReq.reason}</p>
		<p><strong>{oozeReq.instruction}</strong></p>
		<ol>{#each oozeReq.steps as s}<li>{s}</li>{/each}</ol>
		<label style="display:block;margin-top:0.5rem">
			<input type="checkbox" bind:checked={oozeConfirmed} />
			<span class="muted">Saya sudah melakukan &amp; menandai hasil uji ooze</span>
		</label>
		{#if !oozeConfirmed}
			<p class="muted">Diagnosis diblokir sampai uji ooze dikonfirmasi.</p>
		{/if}
	</div>
{/if}

{#if result}
	<div class="card" style="margin-top:1rem;border-color:#86efac">
		<h2>Hasil Diagnosis</h2>
		<p>
			<strong>{result.diagnosis.confirmed_disease}</strong>
			· Keyakinan <span class="badge medium">{result.bayesian.confidence_level}</span>
			· Keparahan {result.diagnosis.severity_pct.toFixed(1)}%
		</p>
		<p class="muted">Posterior:</p>
		<table>
			<thead><tr><th>Penyakit</th><th>Probabilitas</th></tr></thead>
			<tbody>
				{#each Object.entries(result.bayesian.posterior_probabilities).sort((a, b) => b[1] - a[1]) as [k, v]}
					<tr><td>{k}</td><td>{(v * 100).toFixed(1)}%</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
{/if}

{#if protocol}
	<div class="card" style="margin-top:1rem;border-color:#86efac">
		<h2>💊 Resep Tindakan — {protocol.disease}</h2>
		<p><strong>{protocol.action_line}</strong></p>
		<p class="muted">Dosis: {protocol.dose_g_per_l} {protocol.unit} · Interval {protocol.interval_days} hari · {protocol.window}</p>
		<ol>{#each protocol.steps as s}<li>{s}</li>{/each}</ol>
		<p class="muted">⚠️ {protocol.safety}</p>
		{#if protocol.organic_fallback}<p class="muted">🌿 {protocol.organic_fallback}</p>{/if}
	</div>
{/if}
