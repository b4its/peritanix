<script lang="ts">
	import { onMount } from 'svelte';
	import { listLands, soilList, fertilizerPlan, type Land, type FertilizerPlan } from '$lib/api';
	import { prescribeFertilizer } from '$lib/physics';

	let lands = $state<Land[]>([]);
	let landId = $state('');
	let ph = $state(6.5);
	let cec = $state(15);
	let hst = $state(20);
	let targetYield = $state(20);
	let plan = $state<FertilizerPlan | null>(null);
	let source = $state<'server' | 'offline' | ''>('');
	let err = $state('');

	onMount(async () => {
		try {
			lands = await listLands();
			if (lands.length) landId = lands[0].id;
		} catch (e: any) {
			err = e.message;
		}
	});

	async function autofillLand(id: string) {
		try {
			const soils = await soilList(id);
			if (soils.length) {
				ph = soils[0].ph_deterministic || ph;
				cec = soils[0].cec_meq || cec;
			}
		} catch {
			/* best effort */
		}
	}

	async function compute() {
		err = '';
		plan = null;
		source = '';
		try {
			plan = await fertilizerPlan({
				ph,
				cec_meq: cec,
				hst,
				target_yield_t_ha: targetYield
			});
			source = 'server';
		} catch (e: any) {
			// Offline fallback: deterministic mirror.
			plan = prescribeFertilizer(ph, cec, hst, targetYield);
			source = 'offline';
			err = '⚠️ Dihitung offline (server tak terjangkau).';
		}
	}
</script>

<svelte:head>
	<title>DAPE — Kalkulator Pupuk</title>
</svelte:head>

<h1>Kalkulator Pupuk Preskriptif</h1>
<p class="muted">
	Hitung gramasi tepat campuran pupuk kocor per liter air berdasarkan pH, KTK,
	dan umur tanaman (HST). Bekerja offline (mirror deterministik) maupun online.
</p>

{#if err}<p class="muted">{err}</p>{/if}

<div class="grid" style="margin-top:1rem">
	<div class="card">
		<h2>Input Tanah &amp; Tanaman</h2>
		<label class="muted" for="li">Lahan (autofill pH/KTK)</label>
		<select id="li" bind:value={landId} onchange={() => autofillLand(landId)}>
			{#each lands as l}<option value={l.id}>{l.name}</option>{/each}
		</select>
		<div style="display:flex;gap:0.5rem;margin-top:0.5rem">
			<div><label class="muted" for="ph">pH</label><input id="ph" type="number" bind:value={ph} step="0.1" /></div>
			<div><label class="muted" for="ce">KTK (meq)</label><input id="ce" type="number" bind:value={cec} /></div>
		</div>
		<div style="display:flex;gap:0.5rem;margin-top:0.5rem">
			<div><label class="muted" for="hs">HST</label><input id="hs" type="number" bind:value={hst} /></div>
			<div><label class="muted" for="ty">Target (t/ha)</label><input id="ty" type="number" bind:value={targetYield} /></div>
		</div>
		<button class="btn" style="margin-top:0.75rem" onclick={compute}>🧪 Hitung Dosis</button>
	</div>

	<div class="card">
		<h2>Resep Kocor {source ? `(${source})` : ''}</h2>
		{#if !plan}
			<p class="muted">Isi data lalu klik hitung.</p>
		{:else}
			<table>
				<thead><tr><th>Bahan</th><th>Dosis</th></tr></thead>
				<tbody>
					<tr><td>NPK 16-16-16</td><td><strong>{plan.npk_16_16_16_g_per_l} g/L</strong></td></tr>
					<tr><td>KNO₃</td><td><strong>{plan.kno3_g_per_l} g/L</strong></td></tr>
					<tr><td>Kalsium</td><td><strong>{plan.calcium_g_per_l} g/L</strong></td></tr>
					{#if (plan.dolomite_g_per_m2 ?? 0) > 0}
						<tr><td>Dolomit</td><td><strong>{plan.dolomite_g_per_m2} g/m²</strong></td></tr>
					{/if}
				</tbody>
			</table>
			<p class="muted" style="margin-top:0.5rem">{plan.recommendation}</p>
			<p class="muted">
				Contoh takaran untuk 10 liter air: NPK
				{(plan.npk_16_16_16_g_per_l * 10).toFixed(1)} g · KNO₃
				{(plan.kno3_g_per_l * 10).toFixed(1)} g · Kalsium
				{(plan.calcium_g_per_l * 10).toFixed(1)} g.
			</p>
		{/if}
	</div>
</div>
