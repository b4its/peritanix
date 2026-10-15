<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import {
		getLand,
		landDiseases,
		soilList,
		calorimetry,
		recomputeGDD,
		type Land,
		type DiseaseLog,
		type SoilAssessment,
		type CalorimetryResult
	} from '$lib/api';
	import { db, enqueue } from '$lib/db';

	let land = $state<Land | null>(null);
	let diseases = $state<DiseaseLog[]>([]);
	let soils = $state<SoilAssessment[]>([]);
	let err = $state('');

	// Offline calorimetry calculator
	let elevation = $state(850);
	let wellTemp = $state(27);
	let calo = $state<CalorimetryResult | null>(null);
	let caloErr = $state('');

	async function load(id: string) {
		try {
			land = await getLand(id);
			diseases = await landDiseases(id);
			soils = await soilList(id);
			elevation = land.elevation_m || 850;
		} catch (e: any) {
			err = e.message;
		}
	}

	async function doCalo() {
		caloErr = '';
		try {
			calo = await calorimetry(elevation, wellTemp);
		} catch (e: any) {
			// Offline fallback: compute locally.
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
				caloErr = '⚠️ Dihitung offline (server tak terjangkau).';
			} catch (e2: any) {
				caloErr = e2.message;
			}
		}
		// Queue a post-harvest record for sync.
		if (land && calo) {
			await enqueue('harvest_batch', {
				land_id: land.id,
				batch_code: 'BATCH-' + Date.now(),
				boiling_point_calc: calo.boiling_point_c,
				hot_water_ratio: calo.ratio_label
			});
		}
	}

	async function doGDD() {
		if (!land) return;
		try {
			await recomputeGDD(land.id);
			await load(land.id);
		} catch (e: any) {
			err = e.message;
		}
	}

	onMount(() => {
		const id = $page.params.id;
		if (id) load(id);
	});
</script>

<svelte:head>
	<title>DAPE — {land?.name ?? 'Lahan'}</title>
</svelte:head>

{#if err}<p class="error">{err}</p>{/if}
{#if !land}
	<p class="muted">Memuat lahan…</p>
{:else}
	<h1>{land.name}</h1>
	<p class="muted">
		Luas {(land.area_ha ?? 0).toFixed(3)} ha · Elevasi {land.elevation_m} m ·
		Fase <span class="badge medium">{land.current_phase}</span>
	</p>

	<div class="grid" style="margin-top:1rem">
		<div class="card">
			<h2>Fisiologi (GDD)</h2>
			<p>Akumulasi GDD: <strong>{(land.accumulated_gdd ?? 0).toFixed(0)}</strong></p>
			<p class="muted">Tbase cabai = 15 °C. Fase ditentukan oleh umur termal, bukan kalender.</p>
			<button class="btn" onclick={doGDD}>↻ Hitung Ulang GDD</button>
		</div>

		<div class="card">
			<h2>Kalkulator Sterilisasi 52 °C</h2>
			<label class="muted" for="elev">Elevasi (m)</label>
			<input id="elev" type="number" bind:value={elevation} />
			<label class="muted" for="well" style="display:block;margin-top:0.5rem">Suhu air sumur (°C)</label>
			<input id="well" type="number" bind:value={wellTemp} step="0.5" />
			<button class="btn" style="margin-top:0.75rem" onclick={doCalo}>Hitung Takaran Asas Black</button>
			{#if caloErr}<p class="error">{caloErr}</p>{/if}
			{#if calo}
				<p style="margin-top:0.75rem">
					Titik didih lokal: <strong>{calo.boiling_point_c.toFixed(2)} °C</strong>
				</p>
				<p><strong>{calo.ratio_label}</strong></p>
				<p class="muted">{calo.tts_text} (perendaman {calo.countdown_s} detik)</p>
			{/if}
		</div>

		<div class="card">
			<h2>Tanah</h2>
			{#if soils.length === 0}
				<p class="muted">Belum ada data tanah.</p>
			{:else}
				{#each soils.slice(0, 3) as s}
					<p>
						<strong>{s.soil_class}</strong> — pH {s.ph_deterministic ?? '—'} ·
						KTK {s.cec_meq} meq/100g
					</p>
					<p class="muted">Pasir {s.sand_pct}% · Debu {s.silt_pct}% · Liat {s.clay_pct}%</p>
				{/each}
			{/if}
		</div>
	</div>

	<div class="card" style="margin-top:1rem">
		<h2>Riwayat Diagnosis</h2>
		{#if diseases.length === 0}
			<p class="muted">Belum ada diagnosis.</p>
		{:else}
			<table>
				<thead>
					<tr><th>Penyakit</th><th>Keparahan</th><th>Keyakinan</th><th>Protokol</th><th>TWI</th></tr>
				</thead>
				<tbody>
					{#each diseases as d}
						<tr>
							<td>{d.confirmed_disease}</td>
							<td>{(d.severity_pct ?? 0).toFixed(1)}%</td>
							<td>
								<span class="badge {d.confidence_level?.toLowerCase() ?? 'medium'}">
									{d.confidence_level}
								</span>
							</td>
							<td><code>{d.protocol_code}</code></td>
							<td>{(d.local_twi ?? 0).toFixed(2)}</td>
						</tr>
					{/each}
				</tbody>
			</table>
		{/if}
	</div>
{/if}
