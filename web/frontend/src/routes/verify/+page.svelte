<script lang="ts">
	import { onDestroy } from 'svelte';
	import {
		verifyCertificate,
		getHarvestBatch,
		gddHistory,
		type VerifyResult,
		type HarvestBatch,
		type GDDPoint
	} from '$lib/api';
	import { barcodeDetectorSupported, startQrScan } from '$lib/qrscan';
	import Icon from '$lib/Icon.svelte';

	let raw = $state('');
	let result = $state<VerifyResult | null>(null);
	let err = $state('');
	let scanErr = $state('');
	let scanning = $state(false);
	let videoEl = $state<HTMLVideoElement>();
	let stopScan: (() => void) | null = null;
	const scannerSupported = barcodeDetectorSupported();

	// Synced evidence (only present when the farmer's batch reached the server).
	let batch = $state<HarvestBatch | null>(null);
	let gddSeries = $state<GDDPoint[]>([]);
	let lookupNote = $state('');

	async function loadSyncedEvidence(payload: Record<string, unknown>) {
		batch = null;
		gddSeries = [];
		lookupNote = '';
		const code = payload.batch_code as string | undefined;
		if (code) {
			try {
				batch = await getHarvestBatch(code);
				lookupNote = 'Data batch tersinkronisasi ditemukan.';
			} catch {
				lookupNote = 'Batch belum tersinkronisasi ke server (sertifikat tetap sah offline).';
			}
		}
		const landId = payload.land_id as string | undefined;
		if (landId) {
			try {
				const g = await gddHistory(landId, 30);
				gddSeries = g.series ?? [];
			} catch {
				/* ignore — GDD history is best-effort */
			}
		}
	}

	async function toggleScan() {
		if (scanning) {
			stopScan?.();
			scanning = false;
			return;
		}
		scanErr = '';
		try {
			if (!videoEl) throw new Error('Elemen video belum siap.');
			stopScan = await startQrScan(videoEl, (text) => {
				raw = text;
				scanning = false;
				verify();
			});
			scanning = true;
		} catch (e: any) {
			scanErr = e.message;
			scanning = false;
		}
	}

	onDestroy(() => stopScan?.());

	// Decoded payload structure expected in the QR: {payload, signature, public_key}
	async function verify() {
		err = '';
		result = null;
		let cert: any;
		try {
			cert = JSON.parse(raw);
		} catch {
			err = 'QR tidak valid (bukan JSON).';
			return;
		}
		if (!cert.payload || !cert.signature || !cert.public_key) {
			err = 'Struktur sertifikat tidak lengkap (butuh payload, signature, public_key).';
			return;
		}
		try {
			result = await verifyCertificate(cert);
			if (result.valid && result.payload) {
				await loadSyncedEvidence(result.payload);
			}
		} catch (e: any) {
			err = 'Verifikasi gagal: ' + e.message;
		}
	}

	// Simple inline GDD curve path.
	function curvePath(values: number[], w = 320, h = 70): string {
		if (values.length < 2) return '';
		const max = Math.max(...values, 1e-9);
		return values
			.map((v, i) => {
				const x = (i / (values.length - 1)) * w;
				const y = h - (v / max) * h;
				return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
			})
			.join(' ');
	}
</script>

<svelte:head>
	<title>DAPE — Verifikasi Sertifikat QR</title>
</svelte:head>

<h1>Verifikasi Sertifikat Mutu</h1>
<p class="muted">
	Portal publik untuk staf QC / off-taker. Verifikasi tanda tangan Ed25519 —
	bekerja tanpa login dan tanpa perlu HP petani sudah tersinkronisasi.
</p>

<div class="card" style="margin-top:1rem">
	<h2>Pindai QR dengan Kamera</h2>
	{#if scannerSupported}
		<div style="position:relative;max-width:360px">
			<video bind:this={videoEl} style="width:100%;border-radius:8px;background:#000;min-height:200px"></video>
		</div>
		<button class="btn" style="margin-top:0.75rem" onclick={toggleScan}>
			{#if scanning}<Icon name="cancel" size={14} /> Stop{:else}<Icon name="camera" size={14} /> Mulai Pindai{/if}
		</button>
		{#if scanErr}<p class="error">{scanErr}</p>{/if}
	{:else}
		<p class="muted">
			Browser ini tidak mendukung BarcodeDetector. Tempel isi QR secara manual
			di bawah (atau gunakan Chrome/Edge/Android).
		</p>
	{/if}
</div>

<div class="card" style="margin-top:1rem">
	<h2>Atau Tempel Isi QR</h2>
	<textarea
		bind:value={raw}
		rows="8"
		placeholder="Tempel JSON sertifikat: payload, signature, public_key"
		style="width:100%;font-family:monospace;font-size:0.8rem;border:1px solid #cbd5cf;border-radius:8px;padding:0.5rem;box-sizing:border-box"
	></textarea>
	<button class="btn" style="margin-top:0.75rem" onclick={verify}><Icon name="search" size={14} /> Verifikasi</button>
</div>

{#if err}
	<div class="card alert danger" style="margin-top:1rem">
		<p class="error">{err}</p>
	</div>
{/if}

{#if result}
	<div class="card" style="margin-top:1rem">
		<h2>
			{#if result.valid}
				<span class="badge high"><Icon name="check-circle" size={12} /> VALID</span>
			{:else}
				<span class="badge low"><Icon name="cancel" size={12} /> TIDAK VALID</span>
			{/if}
			Hasil Verifikasi
		</h2>
		{#if result.reason}<p class="error">{result.reason}</p>{/if}
		{#if result.valid && result.payload}
			<table>
				<thead><tr><th>Field</th><th>Nilai</th></tr></thead>
				<tbody>
					{#each Object.entries(result.payload) as [k, v]}
						<tr><td><code>{k}</code></td><td>{JSON.stringify(v)}</td></tr>
					{/each}
				</tbody>
			</table>
			<p class="muted" style="margin-top:0.75rem">
				Tanda tangan kriptografis cocok dengan payload. Sertifikat ini sah
				secara matematis.
			</p>
		{/if}
	</div>
{/if}

{#if result?.valid}
	<div class="card" style="margin-top:1rem">
		<h2>Bukti Tersinkronisasi</h2>
		{#if lookupNote}<p class="muted">{lookupNote}</p>{/if}
		{#if batch}
			<table>
				<tbody>
					<tr><td class="muted">Kode batch</td><td><code>{batch.batch_code}</code></td></tr>
					<tr><td class="muted">Titik didih lokal</td><td>{batch.boiling_point_calc} °C</td></tr>
					<tr><td class="muted">Rasio air 52 °C</td><td>{batch.hot_water_ratio}</td></tr>
					<tr><td class="muted">Archimedes sink ratio</td><td>{batch.archimedes_sink_ratio}</td></tr>
					<tr><td class="muted">Grade</td><td><span class="badge high">{batch.grade_result}</span></td></tr>
				</tbody>
			</table>
		{/if}
		{#if gddSeries.length >= 2}
			<p class="muted" style="margin-top:0.75rem">Riwayat akumulasi GDD (30 hari):</p>
			<svg viewBox="0 0 320 70" width="100%" height="80" style="background:#f8faf8;border-radius:8px">
				<path d={curvePath(gddSeries.map((p) => p.gdd_cum))} fill="none" stroke="#d97706" stroke-width="2" />
			</svg>
			<p class="muted">GDD akhir: {gddSeries[gddSeries.length - 1].gdd_cum.toFixed(0)}</p>
		{:else}
			<p class="muted" style="margin-top:0.5rem">
				Riwayat GDD tersedia setelah lahan tersinkronisasi ke server.
			</p>
		{/if}
	</div>
{/if}
