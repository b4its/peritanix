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
	import {
		analyseLeaf,
		visualLikelihoods,
		differentiateLeafCurl,
		type LeafAnalysis,
		type CurlDiagnosis
	} from '$lib/vision';
	import Icon from '$lib/Icon.svelte';

	let lands = $state<Land[]>([]);
	let landId = $state('');
	let lon = $state(107.6035);
	let lat = $state(-6.9035);
	let localTwi = $state(9.0);
	let wind = $state(135);
	let imageUrl = $state('');
	let analysis = $state<LeafAnalysis | null>(null);
	let curlDiag = $state<CurlDiagnosis | null>(null);
	let curlDirection = $state<'none' | 'up' | 'down'>('none');
	let undersideColor = $state<'normal' | 'silvery' | 'bronze'>('normal');
	let sunkenLesions = $state(false);
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

	function updateCurl() {
		if (!analysis) return;
		curlDiag = differentiateLeafCurl(analysis, {
			curlDirection,
			undersideColor,
			sunkenLesions
		});
	}

	async function onFile(e: Event) {
		const input = e.target as HTMLInputElement;
		const file = input.files?.[0];
		if (!file) return;
		imageUrl = URL.createObjectURL(file);
		try {
			analysis = await analyseLeaf(imageUrl);
			analysisSource = 'offline';
			updateCurl();
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
	Foto daun/buah <Icon name="chevron-right" size={12} /> analisis offline di perangkat (severitas &amp; warna)
	<Icon name="chevron-right" size={12} /> fusi Bayes server (visual + spasial + TWI)
	<Icon name="chevron-right" size={12} /> resep tindakan. Bekerja offline untuk analisis awal,
	tersinkron saat ada sinyal.
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

			<div style="margin-top:0.75rem;padding:0.75rem;background:#f8fafc;border-radius:6px;border:1px solid #e2e8f0">
				<strong style="display:block;margin-bottom:0.4rem;font-size:0.9rem">Morfologi Gejala Daun (Opsi 1 Edge CV):</strong>
				<div style="display:flex;gap:0.5rem;flex-wrap:wrap">
					<div>
						<label class="muted" for="cd" style="font-size:0.8rem">Arah Keriting:</label>
						<select id="cd" bind:value={curlDirection} onchange={updateCurl} style="padding:2px 6px;font-size:0.85rem">
							<option value="none">Otomatis (Piksel)</option>
							<option value="up">Melengkung ke Atas (Thrips)</option>
							<option value="down">Melengkung ke Bawah (Tungau/Mites)</option>
						</select>
					</div>
					<div>
						<label class="muted" for="uc" style="font-size:0.8rem">Permukaan Bawah:</label>
						<select id="uc" bind:value={undersideColor} onchange={updateCurl} style="padding:2px 6px;font-size:0.85rem">
							<option value="normal">Normal</option>
							<option value="silvery">Keperakan (Thrips)</option>
							<option value="bronze">Kecokelatan / Tembaga (Mites)</option>
						</select>
					</div>
				</div>
				<label style="display:flex;align-items:center;gap:0.4rem;margin-top:0.4rem;font-size:0.85rem">
					<input type="checkbox" bind:checked={sunkenLesions} onchange={updateCurl} />
					<span>Bercak cekung melingkar konsentris (Antraknosa / Patek)</span>
				</label>
			</div>
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
			<Icon name="brain" size={15} /> {busy ? 'Mengevaluasi…' : 'Evaluasi Diagnosis'}
		</button>
	</div>
</div>

{#if curlDiag}
	<div class="card" style="margin-top:1rem;border-color:var(--orange-200);background:var(--orange-50)">
		<div class="row" style="justify-content:space-between">
			<h2 style="margin:0;color:var(--orange-700)">
				<Icon name="microscope" size={16} /> Diferensiasi Patologi On-Device (Opsi 1)
			</h2>
			<span class="badge accent">{(curlDiag.confidence * 100).toFixed(0)}% Yakin</span>
		</div>
		<p style="margin-top:0.5rem;font-size:1.05rem"><strong>{curlDiag.title}</strong></p>
		<p style="margin:0.25rem 0">{curlDiag.description}</p>
		<ul style="margin:0.4rem 0 0.8rem 1.2rem;font-size:0.9rem">
			{#each curlDiag.symptoms as s}<li>{s}</li>{/each}
		</ul>
		<div style="background:var(--surface);padding:0.75rem;border-radius:var(--radius-sm);border:1px solid var(--orange-100)">
			<strong style="color:var(--orange-700);display:inline-flex;align-items:center;gap:0.35rem">
				<Icon name="pill" size={15} /> Rekomendasi Preskriptif Offline
			</strong>
			<p style="margin:0.25rem 0"><strong>Bahan Aktif:</strong> {curlDiag.active_ingredient}</p>
			<p style="margin:0.25rem 0"><strong>Dosis:</strong> {curlDiag.dosage_per_liter}</p>
			<p style="margin:0.25rem 0;font-size:0.9rem" class="muted">{curlDiag.spraying_instructions}</p>
			{#if curlDiag.organic_alternative}
				<p style="margin:0.25rem 0;font-size:0.9rem;color:var(--ok)">
					<Icon name="leaf" size={14} /> <strong>Organik/Biokontrol:</strong> {curlDiag.organic_alternative}
				</p>
			{/if}
		</div>
	</div>
{/if}

{#if oozeReq}
	<div class="card alert danger" style="margin-top:1rem">
		<h2 style="color:var(--danger)"><Icon name="lock" size={16} /> Uji Ooze Wajib (Destructive Test)</h2>
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
	<div class="card" style="margin-top:1rem;border-color:var(--orange-200)">
		<h2><Icon name="pill" size={16} /> Resep Tindakan — {protocol.disease}</h2>
		<p><strong>{protocol.action_line}</strong></p>
		<p class="muted">Dosis: {protocol.dose_g_per_l} {protocol.unit} · Interval {protocol.interval_days} hari · {protocol.window}</p>
		<ol>{#each protocol.steps as s}<li>{s}</li>{/each}</ol>
		<p class="muted"><Icon name="warning" size={13} /> {protocol.safety}</p>
		{#if protocol.organic_fallback}<p class="muted"><Icon name="leaf" size={13} /> {protocol.organic_fallback}</p>{/if}
	</div>
{/if}
