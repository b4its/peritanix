<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import {
		listLands,
		landGeoJSON,
		landDiseases,
		soilFertilityGeoJSON,
		type Land,
		type DiseaseLog
	} from '$lib/api';
	import {
		heatCanvasLayer,
		windVectorLayer,
		fertilityLayer,
		diseaseHeatPoints,
		type WindVector
	} from '$lib/heatmap';

	let lands = $state<Land[]>([]);
	let diseases = $state<DiseaseLog[]>([]);
	let geo = $state<any>(null);
	let fertility = $state<any>(null);
	let mapEl: HTMLDivElement;
	let map: any;
	let err = $state('');
	let showFertility = $state(true);

	// A demo prevailing wind (SE trades, blowing from ~135°).
	let wind: WindVector[] = [];

	onMount(async () => {
		if (!browser) return;
		try {
			lands = await listLands();
			geo = await landGeoJSON();
			fertility = await soilFertilityGeoJSON().catch(() => null);
			for (const l of lands) {
				const ds = await landDiseases(l.id).catch(() => []);
				diseases = diseases.concat(ds);
			}
		} catch (e: any) {
			err = e.message;
		}
		const L = (await import('leaflet')).default;
		await import('leaflet/dist/leaflet.css');
		map = L.map(mapEl).setView([-6.9035, 107.6035], 15);
		L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
			attribution: '© OpenStreetMap'
		}).addTo(map);
		if (geo) {
			L.geoJSON(geo, {
				style: () => ({ color: '#16a34a', weight: 2, fillOpacity: 0.1 }),
				onEachFeature: (f: any, layer: any) => {
					layer.bindPopup(
						`<strong>${f.properties.name}</strong><br/>` +
							`Luas: ${(f.properties.area_ha ?? 0).toFixed(3)} ha<br/>` +
							`Fase: ${f.properties.phase}<br/>GDD: ${(f.properties.gdd ?? 0).toFixed(0)}`
					);
				}
			}).addTo(map);
		}
		// Soil-fertility layer (coloured by pH class).
		if (fertility) {
			fertilityLayer(L, fertility).addTo(map);
		}
		// Disease heatmap layer.
		const pts = diseaseHeatPoints(diseases);
		if (pts.length > 0) {
			heatCanvasLayer(L, pts).addTo(map);
		}
		// Wind vector layer.
		wind = [{ lat: -6.9035, lng: 107.6035, direction_deg: 135, speed: 1 }];
		windVectorLayer(L, wind).addTo(map);
	});
</script>

<svelte:head>
	<title>DAPE — Dashboard Geospasial</title>
</svelte:head>

<h1>Dashboard Geospasial</h1>
{#if err}<p class="error">{err}</p>{/if}

<div class="card" style="padding:0;overflow:hidden">
	<div bind:this={mapEl} style="height:420px;width:100%"></div>
</div>
<p class="muted" style="margin-top:0.5rem">
	Layer: <strong>{diseases.length}</strong> titik penyakit (heatmap) ·
	<strong>{fertility?.features?.length ?? 0}</strong> titik kesuburan (pH) ·
	vektor angin dominan 135° (dari tenggara).
</p>
<p class="muted">
	Legenda pH: <span style="color:#16a34a">● optimal</span> ·
	<span style="color:#d97706">● masam (&lt;5.5)</span> ·
	<span style="color:#2563eb">● basa (&gt;7.0)</span>
</p>

<div class="grid" style="margin-top:1rem">
	<div class="card">
		<h2>Fortification Compliance Monitor</h2>
		<p class="muted">Lahan dalam fase Antesis (wajib Ca+B+Si, Urea diblokir):</p>
		{#if lands.filter((l) => l.current_phase === 'FORTIFICATION_LOCKED').length === 0}
			<p class="muted">Tidak ada lahan terkunci saat ini.</p>
		{:else}
			<ul>
				{#each lands.filter((l) => l.current_phase === 'FORTIFICATION_LOCKED') as l}
					<li>
						<strong>{l.name}</strong> —
						<span class="badge medium">FORTIFICATION_LOCKED</span>
						(GDD {(l.accumulated_gdd ?? 0).toFixed(0)})
					</li>
				{/each}
			</ul>
		{/if}
	</div>
	<div class="card">
		<h2>Spatiotemporal Disease Heatmap</h2>
		<p class="muted">
			Sebaran titik penyakit per lahan dengan indikator vektor angin vs
			cekungan drainase (TWI tinggi).
		</p>
		<p class="muted">Total titik terdeteksi: <strong>{diseases.length}</strong></p>
	</div>
	<div class="card">
		<h2>Peta Lahan</h2>
		<p class="muted">{lands.length} poligon lahan (GeoJSON dari PostGIS).</p>
	</div>
</div>
