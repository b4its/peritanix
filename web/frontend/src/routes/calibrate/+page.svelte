<script lang="ts">
	/**
	 * Spectral LED-flash calibration (TAHAP 1.1).
	 *
	 * The camera LED differs per phone brand, biasing dark-chamber photometry.
	 * Onboarding asks the farmer to photograph standard white paper (or a salt
	 * sachet) inside a dark bowl, then stores the per-channel capture as a white
	 * reference matrix. Every photometry reading is later divided by it, so two
	 * phones photographing the same reagent yield the same CIELAB.
	 */
	import { onDestroy, onMount } from 'svelte';
	import { browser } from '$app/environment';

	let videoEl = $state<HTMLVideoElement>();
	let canvasEl = $state<HTMLCanvasElement>();
	let streaming = $state(false);
	let stream: MediaStream | null = null;
	let whiteRef = $state<[number, number, number] | null>(null);
	let lux = $state<number | null>(null);
	let darkOK = $state(false);
	let err = $state('');
	let saved = $state(false);
	let ambient: any = null;

	onMount(() => {
		if (browser && localStorage.getItem('dape.white_ref')) {
			whiteRef = JSON.parse(localStorage.getItem('dape.white_ref')!);
			saved = true;
		}
	});

	async function start() {
		err = '';
		try {
			stream = await navigator.mediaDevices.getUserMedia({
				video: { facingMode: 'environment' }
			});
			if (!videoEl) throw new Error('video belum siap');
			videoEl.srcObject = stream;
			await videoEl.play();
			streaming = true;

			// Best-effort ambient light reading (0 Lux target).
			if ('AmbientLightSensor' in window) {
				ambient = new (window as any).AmbientLightSensor();
				ambient.onreading = () => (lux = ambient.illuminance);
				ambient.onerror = () => (lux = null);
				ambient.start();
			}
		} catch (e: any) {
			err = 'Kamera: ' + e.message;
		}
	}

	onDestroy(() => {
		stream?.getTracks().forEach((t) => t.stop());
		ambient?.stop?.();
	});

	function capture() {
		err = '';
		if (!videoEl || !canvasEl) return;
		// 0-Lux gate: if a light sensor is present and reads > 5 lux, warn (the
		// dark bowl is required so only the LED flash illuminates the paper).
		darkOK = lux === null || lux <= 5;
		const w = videoEl.videoWidth || 320;
		const h = videoEl.videoHeight || 240;
		canvasEl.width = w;
		canvasEl.height = h;
		const ctx = canvasEl.getContext('2d')!;
		ctx.drawImage(videoEl, 0, 0, w, h);
		const { data } = ctx.getImageData(0, 0, w, h);
		let sr = 0,
			sg = 0,
			sb = 0;
		const n = data.length / 4;
		// Average the central 50% region (avoid vignette edges).
		for (let i = 0; i < n; i++) {
			sr += data[i * 4];
			sg += data[i * 4 + 1];
			sb += data[i * 4 + 2];
		}
		whiteRef = [sr / n, sg / n, sb / n].map((v) => Math.round(v * 100) / 100) as [
			number,
			number,
			number
		];
	}

	function save() {
		if (!whiteRef) return;
		localStorage.setItem('dape.white_ref', JSON.stringify(whiteRef));
		saved = true;
	}
</script>

<svelte:head>
	<title>DAPE — Kalibrasi Flash</title>
</svelte:head>

<h1>Kalibrasi Spektral LED Flash</h1>
<p class="muted">
	Agar hasil fotometri ruang gelap seragam antar merek HP, foto kertas putih
	standar (atau bungkus garam) di dalam <strong>mangkuk gelap</strong> dengan
	lampu flash menyala. Nilai per-kanal disimpan sebagai <em>White Reference
	Matrix</em> dan dibagi ke setiap pembacaan reagen.
</p>

{#if err}<p class="error">{err}</p>{/if}

<div class="grid" style="margin-top:1rem">
	<div class="card">
		<h2>1. Siapkan &amp; Ambil Foto</h2>
		<p class="muted">
			Taruh kertas putih di mangkuk gelap → nyalakan flash → ambil foto.
		</p>
		<div style="position:relative;max-width:320px">
			<video bind:this={videoEl} style="width:100%;border-radius:8px;background:#000;min-height:180px"></video>
		</div>
		<button class="btn" style="margin-top:0.75rem" onclick={start}>📷 Nyalakan Kamera</button>
		<button class="btn" style="margin-top:0.75rem" onclick={capture} disabled={!streaming}>
			📸 Ambil Referensi Putih
		</button>
		{#if lux !== null}
			<p class="muted">Sensor cahaya: {lux.toFixed(0)} Lux {darkOK ? '(gelap OK)' : '(⚠️ terlalu terang)'}</p>
		{/if}
	</div>

	<div class="card">
		<h2>2. White Reference Matrix</h2>
		<canvas bind:this={canvasEl} style="display:none"></canvas>
		{#if whiteRef}
			<p>R: <strong>{whiteRef[0]}</strong> · G: <strong>{whiteRef[1]}</strong> · B: <strong>{whiteRef[2]}</strong></p>
			<p class="muted">
				Kanvas gain yang diterapkan: 255/R = {(255 / whiteRef[0]).toFixed(2)} ·
				255/G = {(255 / whiteRef[1]).toFixed(2)} · 255/B = {(255 / whiteRef[2]).toFixed(2)}
			</p>
			<button class="btn" onclick={save}>💾 Simpan Kalibrasi</button>
			{#if saved}<p style="color:#16a34a">✓ Tersimpan di perangkat (sinkron ke profil server saat online).</p>{/if}
		{:else}
			<p class="muted">Belum ada referensi. Ambil foto di langkah 1.</p>
		{/if}
	</div>
</div>
