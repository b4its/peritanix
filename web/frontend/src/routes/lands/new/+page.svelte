<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { browser } from '$app/environment';
	import { initLand, type LandInitResult } from '$lib/api';
	import { toPolygonWKT } from '$lib/geo';

	let mapEl: HTMLDivElement;
	let map: any;
	let L: any;
	let drawn: any = null;
	let vertices: [number, number][] = []; // [lat, lng]
	let markerLayer: any;
	let result = $state<LandInitResult | null>(null);
	let err = $state('');
	let busy = $state(false);
	let landName = $state('');
	let elevation = $state(850);
	let plantingDate = $state('');
	let gpsWatching = $state(false);
	let watchId: number | null = null;
	let userId = $state('11111111-1111-1111-1111-111111111111');

	onMount(async () => {
		if (!browser) return;
		L = (await import('leaflet')).default;
		await import('leaflet/dist/leaflet.css');
		map = L.map(mapEl).setView([-6.9035, 107.6035], 16);
		L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
			attribution: '© OpenStreetMap'
		}).addTo(map);
		markerLayer = L.layerGroup().addTo(map);
	});

	onDestroy(() => {
		if (watchId !== null) navigator.geolocation.clearWatch(watchId);
	});

	function addVertex(lat: number, lng: number) {
		vertices = [...vertices, [lat, lng]];
		L.marker([lat, lng], { icon: L.divIcon({ className: 'vtx', html: '●' }) }).addTo(markerLayer);
		renderPolygon();
	}

	function renderPolygon() {
		if (drawn) {
			map.removeLayer(drawn);
			drawn = null;
		}
		if (vertices.length >= 3) {
			drawn = L.polygon(vertices, { color: '#16a34a', fillOpacity: 0.25 }).addTo(map);
		} else if (vertices.length === 2) {
			drawn = L.polyline(vertices, { color: '#16a34a' }).addTo(map);
		}
	}

	// Walk the perimeter: capture a GPS vertex as the farmer moves.
	function toggleGps() {
		if (gpsWatching) {
			if (watchId !== null) navigator.geolocation.clearWatch(watchId);
			watchId = null;
			gpsWatching = false;
			return;
		}
		if (!('geolocation' in navigator)) {
			err = 'Geolocation tidak didukung. Klik peta untuk menambah titik.';
			return;
		}
		watchId = navigator.geolocation.watchPosition(
			(pos) => {
				const { latitude, longitude, altitude } = pos.coords;
				addVertex(latitude, longitude);
				if (altitude != null && altitude > 0) elevation = Math.round(altitude);
				map.setView([latitude, longitude], map.getZoom());
			},
			(e) => (err = 'GPS: ' + e.message),
			{ enableHighAccuracy: true, maximumAge: 1000 }
		);
		gpsWatching = true;
	}

	function undo() {
		vertices = vertices.slice(0, -1);
		markerLayer.clearLayers();
		vertices.forEach(([la, ln]) => L.marker([la, ln]).addTo(markerLayer));
		renderPolygon();
	}

	function reset() {
		vertices = [];
		markerLayer.clearLayers();
		if (drawn) {
			map.removeLayer(drawn);
			drawn = null;
		}
	}

	// Build a closed WKT polygon (lon lat order for EPSG:4326).
	function toWKT(): string {
		return toPolygonWKT(vertices);
	}

	async function submit() {
		err = '';
		result = null;
		if (!landName.trim()) {
			err = 'Nama lahan wajib diisi.';
			return;
		}
		let wkt: string;
		try {
			wkt = toWKT();
		} catch (e: any) {
			err = e.message;
			return;
		}
		busy = true;
		try {
			result = await initLand({
				user_id: userId,
				name: landName,
				boundary_wkt: wkt,
				elevation_m: elevation,
				planting_date: plantingDate || undefined
			});
		} catch (e: any) {
			err = e.message;
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head>
	<title>DAPE — Inisialisasi Lahan</title>
</svelte:head>

<h1>Inisialisasi Lahan (Alur 0)</h1>
<p class="muted">
	Jalankan mengelilingi batas lahan dengan GPS, atau klik peta untuk menambah
	titik. Setelah poligon tertutup, sistem menyimpan batas ke PostGIS dan
	mengunduh cache TWI (&lt; 500 KB) untuk dioperasikan offline.
</p>

<div class="card" style="padding:0;overflow:hidden">
	<div bind:this={mapEl} style="height:400px;width:100%"></div>
</div>

<div class="grid" style="margin-top:1rem">
	<div class="card">
		<h2>Poligon</h2>
		<p>Titik: <strong>{vertices.length}</strong></p>
		<div style="display:flex;gap:0.5rem;flex-wrap:wrap">
			<button class="btn" onclick={toggleGps}>{gpsWatching ? '⏹ Stop GPS' : '📍 Mulai Rekam GPS'}</button>
			<button class="btn secondary" onclick={undo}>↶ Hapus Titik</button>
			<button class="btn secondary" onclick={reset}>🗑 Reset</button>
		</div>
	</div>
	<div class="card">
		<h2>Detail Lahan</h2>
		<label class="muted" for="ln">Nama lahan</label>
		<input id="ln" bind:value={landName} placeholder="Bedengan Utara" />
		<label class="muted" for="el" style="display:block;margin-top:0.5rem">Elevasi (m)</label>
		<input id="el" type="number" bind:value={elevation} />
		<label class="muted" for="pd" style="display:block;margin-top:0.5rem">Tanggal tanam</label>
		<input id="pd" type="date" bind:value={plantingDate} />
		<button class="btn" style="margin-top:0.75rem" onclick={submit} disabled={busy}>
			{busy ? 'Menyimpan…' : '💾 Simpan Lahan'}
		</button>
	</div>
</div>

{#if err}
	<div class="card" style="margin-top:1rem;border-color:#fca5a5"><p class="error">{err}</p></div>
{/if}

{#if result}
	<div class="card" style="margin-top:1rem;border-color:#86efac">
		<h2>✅ Lahan dibuat</h2>
		<p><strong>{result.land.name}</strong> — {(result.land.area_ha ?? 0).toFixed(3)} ha</p>
		<p class="muted">
			Cache TWI: {(result.cache_kb ?? 0).toFixed(1)} KB ·
			{result.twi_cache?.cells?.length ?? 0} sel ·
			risiko maks {result.twi_cache?.max_risk ?? '—'}
		</p>
		<a class="btn" href={`/lands/${result.land.id}`}>Lihat Detail Lahan</a>
	</div>
{/if}

<style>
	:global(.vtx) { color: #16a34a; font-size: 14px; }
</style>
