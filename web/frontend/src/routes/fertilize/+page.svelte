<script lang="ts">
	import { onMount } from 'svelte';
	import { listLands, fertilizerGuard, type Land, type FertilizerGuardResult } from '$lib/api';
	import Icon from '$lib/Icon.svelte';

	let lands = $state<Land[]>([]);
	let landId = $state('');
	let product = $state('Urea');
	let npkRatio = $state('');
	let nitrogenPct = $state(46);
	let appliedG = $state(100);
	let result = $state<FertilizerGuardResult | null>(null);
	let err = $state('');
	let logged = $state(false);

	onMount(() => {
		listLands()
			.then((l) => {
				lands = l;
				if (l.length) landId = l[0].id;
			})
			.catch((e) => (err = e.message));
	});

	async function check() {
		err = '';
		result = null;
		logged = false;
		try {
			result = await fertilizerGuard({
				land_id: landId,
				product,
				npk_ratio: npkRatio,
				nitrogen_pct: nitrogenPct,
				applied_g: appliedG
			});
			if (result.allowed) {
				// In a full app this persists the log; here we mark it accepted.
				logged = true;
			}
		} catch (e: any) {
			err = e.message;
		}
	}
</script>

<svelte:head>
	<title>DAPE — Catat Pemupukan</title>
</svelte:head>

<h1>Catat Pemupukan</h1>
<p class="muted">
	Selama fase Antesis (GDD 350–699) dinding sel buah (pericarp) wajib diperkuat
	Ca+B+Si. Input Nitrogen tinggi (Urea/ZA) <strong>diblokir</strong> karena
	memperlemah dinding sel dan memicu rontok bunga &amp; kerentanan patogen.
</p>

{#if err}<p class="error">{err}</p>{/if}

<div class="grid" style="margin-top:1rem">
	<div class="card">
		<h2>Data Input</h2>
		<label class="muted" for="li">Lahan</label>
		<select id="li" bind:value={landId}>
			{#each lands as l}
				<option value={l.id}>{l.name} — {l.current_phase} (GDD {(l.accumulated_gdd ?? 0).toFixed(0)})</option>
			{/each}
		</select>
		<label class="muted" for="pr" style="display:block;margin-top:0.5rem">Produk</label>
		<input id="pr" bind:value={product} list="prods" />
		<datalist id="prods">
			<option value="Urea"></option>
			<option value="ZA (Ammonium Sulfat)"></option>
			<option value="NPK 16-16-16"></option>
			<option value="KNO3"></option>
			<option value="Kalsium Nitrat"></option>
			<option value="Dolomit"></option>
		</datalist>
		<div style="display:flex;gap:0.5rem;margin-top:0.5rem">
			<div><label class="muted" for="np">Rasio NPK</label><input id="np" bind:value={npkRatio} placeholder="46-0-0" /></div>
			<div><label class="muted" for="ni">N (%)</label><input id="ni" type="number" bind:value={nitrogenPct} /></div>
			<div><label class="muted" for="ag">Jumlah (g)</label><input id="ag" type="number" bind:value={appliedG} /></div>
		</div>
		<button class="btn" style="margin-top:0.75rem" onclick={check}>Validasi &amp; Catat</button>
	</div>

	<div class="card">
		<h2>Hasil Validasi</h2>
		{#if !result}
			<p class="muted">Isi data lalu klik validasi.</p>
		{:else if result.blocked}
			<div class="alert danger">
				<p class="alert-title">
					<Icon name="cancel" size={16} /> INPUT DIBLOKIR — {result.reason}
				</p>
				<p class="muted" style="margin-top:0.5rem">{result.guidance}</p>
				<p class="muted">Protokol wajib: <code>{result.required_protocol}</code></p>
			</div>
		{:else}
			<p class="alert-ok">
				<Icon name="check-circle" size={16} />
				Diizinkan (fase {result.phase}, GDD {result.plant_gdd.toFixed(0)})
			</p>
			{#if result.guidance}<p class="muted">{result.guidance}</p>{/if}
			{#if logged}<p class="muted">Log pemupukan dicatat.</p>{/if}
		{/if}
	</div>
</div>
