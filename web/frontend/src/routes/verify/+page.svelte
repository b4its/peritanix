<script lang="ts">
	import { onDestroy } from 'svelte';
	import { verifyCertificate, type VerifyResult } from '$lib/api';
	import { barcodeDetectorSupported, startQrScan } from '$lib/qrscan';

	let raw = $state('');
	let result = $state<VerifyResult | null>(null);
	let err = $state('');
	let scanErr = $state('');
	let scanning = $state(false);
	let videoEl: HTMLVideoElement;
	let stopScan: (() => void) | null = null;
	const scannerSupported = barcodeDetectorSupported();

	async function toggleScan() {
		if (scanning) {
			stopScan?.();
			scanning = false;
			return;
		}
		scanErr = '';
		try {
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
		} catch (e: any) {
			err = 'Verifikasi gagal: ' + e.message;
		}
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
			{scanning ? '⏹ Stop' : '📷 Mulai Pindai'}
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
	<button class="btn" style="margin-top:0.75rem" onclick={verify}>🔍 Verifikasi</button>
</div>

{#if err}
	<div class="card" style="margin-top:1rem;border-color:#fca5a5">
		<p class="error">{err}</p>
	</div>
{/if}

{#if result}
	<div class="card" style="margin-top:1rem">
		<h2>
			{#if result.valid}
				<span class="badge high">✅ VALID</span>
			{:else}
				<span class="badge low">❌ TIDAK VALID</span>
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
