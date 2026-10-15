<script lang="ts">
	import { economics, type EconomicsResult } from '$lib/api';

	let areaHa = $state(0.5);
	let pricePerKg = $state(25000);
	let baselineYield = $state(12);
	let fertilizerCost = $state(8000000);
	let outbreakProb = $state(0.35);
	let res = $state<EconomicsResult | null>(null);
	let err = $state('');

	function idr(v: number): string {
		return 'Rp ' + Math.round(v).toLocaleString('id-ID');
	}

	async function calc() {
		err = '';
		try {
			res = await economics({
				area_ha: areaHa,
				price_per_kg: pricePerKg,
				baseline_yield_t_ha: baselineYield,
				fertilizer_cost_per_ha_idr: fertilizerCost,
				outbreak_probability: outbreakProb
			});
		} catch (e: any) {
			err = e.message;
		}
	}

	if (typeof window !== 'undefined') calc();
</script>

<svelte:head>
	<title>DAPE — Dampak Ekonomi</title>
</svelte:head>

<h1>Dampak Ekonomi (ROI)</h1>
<p class="muted">
	Apakah DAPE balik modal? Model transparan &amp; dapat dikalibrasi: hemat pupuk
	(20–30%), cegah kehilangan panen (deteksi dini), dan uplift hasil (penguatan
	dinding sel Ca/B).
</p>

{#if err}<p class="error">{err}</p>{/if}

<div class="grid" style="margin-top:1rem">
	<div class="card">
		<h2>Parameter Lahan</h2>
		<label class="muted" for="ar">Luas (ha)</label>
		<input id="ar" type="number" bind:value={areaHa} step="0.1" />
		<label class="muted" for="yt" style="display:block;margin-top:0.5rem">Hasil baseline (ton/ha)</label>
		<input id="yt" type="number" bind:value={baselineYield} step="0.5" />
		<label class="muted" for="pr" style="display:block;margin-top:0.5rem">Harga jual (Rp/kg)</label>
		<input id="pr" type="number" bind:value={pricePerKg} step="1000" />
		<label class="muted" for="fc" style="display:block;margin-top:0.5rem">Biaya pupuk (Rp/ha/musim)</label>
		<input id="fc" type="number" bind:value={fertilizerCost} step="500000" />
		<label class="muted" for="op" style="display:block;margin-top:0.5rem">Probabilitas serangan penyakit (0–1)</label>
		<input id="op" type="number" bind:value={outbreakProb} step="0.05" min="0" max="1" />
		<button class="btn" style="margin-top:0.75rem" onclick={calc}>💰 Hitung Dampak</button>
	</div>

	<div class="card">
		<h2>Hasil</h2>
		{#if res}
			<table>
				<tbody>
					<tr><td class="muted">Pendapatan baseline</td><td>{idr(res.baseline_revenue_idr)}</td></tr>
					<tr><td class="muted">Pendapatan dgn DAPE</td><td>{idr(res.darif_revenue_idr)}</td></tr>
					<tr><td class="muted">Hemat pupuk</td><td style="color:#16a34a">{idr(res.fertilizer_saving_idr)}</td></tr>
					<tr><td class="muted">Uplift hasil</td><td style="color:#16a34a">{idr(res.yield_uplift_idr)}</td></tr>
					<tr><td class="muted">Kehilangan terhindar</td><td style="color:#16a34a">{idr(res.avoided_loss_idr)}</td></tr>
					<tr><td class="muted"><strong>Total manfaat</strong></td><td><strong style="color:#16a34a">{idr(res.total_benefit_idr)}</strong></td></tr>
					<tr><td class="muted">Perkiraan biaya DAPE</td><td>{idr(res.estimated_darif_cost_idr)}</td></tr>
					<tr><td class="muted"><strong>Manfaat bersih</strong></td><td><strong>{idr(res.net_benefit_idr)}</strong></td></tr>
					<tr><td class="muted">ROI</td><td><span class="badge {res.roi_pct > 0 ? 'high' : 'low'}">{res.roi_pct}%</span></td></tr>
					<tr><td class="muted">Break-even (fraksi biaya)</td><td>{(res.break_even_fraction * 100).toFixed(1)}%</td></tr>
				</tbody>
			</table>
			{#each res.notes as n}<p class="muted">ℹ️ {n}</p>{/each}
		{:else}
			<p class="muted">Isi parameter lalu klik hitung.</p>
		{/if}
	</div>
</div>
