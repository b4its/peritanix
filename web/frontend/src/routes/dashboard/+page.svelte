<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import { listLands, landGeoJSON, fortificationCompliance, type Land } from '$lib/api';

	let lands = $state<Land[]>([]);
	let geo = $state<any>(null);
	let mapEl: HTMLDivElement;
	let map: any;
	let err = $state('');

	const compliance = $derived(fortificationCompliance(lands));

	onMount(async () => {
		if (!browser) return;
		try {
			lands = await listLands();
			geo = await landGeoJSON();
		} catch (e: any) {
			err = e.message;
		}
		// Lazy-load Leaflet (avoids SSR issues).
		const L = (await import('leaflet')).default;
		await import('leaflet/dist/leaflet.css');
		map = L.map(mapEl).setView([-6.9035, 107.6035], 15);
		L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
			attribution: '© OpenStreetMap'
		}).addTo(map);
		if (geo) {
			L.geoJSON(geo, {
				style: () => ({ color: '#16a34a', weight: 2, fillOpacity: 0.25 }),
				onEachFeature: (f: any, layer: any) => {
					layer.bindPopup(
						`<strong>${f.properties.name}</strong><br/>` +
							`Luas: ${(f.properties.area_ha ?? 0).toFixed(3)} ha<br/>` +
							`Fase: ${f.properties.phase}<br/>GDD: ${(f.properties.gdd ?? 0).toFixed(0)}`
					);
				}
			}).addTo(map);
		}
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

<div class="grid" style="margin-top:1rem">
	<div class="card">
		<h2>Fortification Compliance Monitor</h2>
		<p class="muted">Lahan dalam fase Antesis (wajib Ca+B+Si, Urea diblokir):</p>
		{#if compliance.locked.length === 0}
			<p class="muted">Tidak ada lahan terkunci saat ini.</p>
		{:else}
			<ul>
				{#each compliance.locked as l}
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
		<h2>Peta Lahan</h2>
		<p class="muted">{lands.length} poligon lahan terdaftar (GeoJSON dari PostGIS).</p>
	</div>
	<div class="card">
		<h2>Catatan</h2>
		<p class="muted">
			Peta offline menggunakan cache Service Worker saat koneksi terputus.
		</p>
	</div>
</div>
