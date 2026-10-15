<script lang="ts">
	import { browser } from '$app/environment';
	import { listLands, type Land } from '$lib/api';
	import { generateKeyPair, signCertificate, type SignedCertificate } from '$lib/sign';

	let lands = $state<Land[]>([]);
	let landId = $state('');
	let gdd = $state(1100);
	let cecMeq = $state(15);
	let fortificationCompliant = $state(true);
	let sterilizationC = $state(52);
	let sinkRatio = $state(0.82);
	let grade = $state('A');
	let payloadHash = $state('');
	let cert = $state<SignedCertificate | null>(null);
	let qrDataUrl = $state('');
	let err = $state('');
	let copied = $state(false);

	// Persist a browser keypair across reloads (dev convenience; real device
	// uses the Android Keystore / mobile signer).
	let keyPair: CryptoKeyPair | null = null;

	async function loadKey(): Promise<CryptoKeyPair> {
		if (keyPair) return keyPair;
		keyPair = await generateKeyPair();
		return keyPair;
	}

	async function issue() {
		err = '';
		cert = null;
		qrDataUrl = '';
		if (!browser) return;
		try {
			await loadKey();
			const payload = {
				land_id: landId,
				gdd: gdd,
				cec_meq: cecMeq,
				fortification_compliant: fortificationCompliant,
				sterilization_c: sterilizationC,
				archimedes_sink_ratio: sinkRatio,
				grade_result: grade,
				payload_hash: payloadHash || 'none',
				ts: Math.floor(Date.now() / 1000)
			};
			cert = await signCertificate(payload, keyPair!);
			const QR = (await import('qrcode')).default;
			qrDataUrl = await QR.toDataURL(JSON.stringify(cert), { width: 320, margin: 1 });
		} catch (e: any) {
			err = e.message;
		}
	}

	async function copyJSON() {
		if (!cert) return;
		await navigator.clipboard.writeText(JSON.stringify(cert));
		copied = true;
		setTimeout(() => (copied = false), 1500);
	}

	if (browser) {
		listLands()
			.then((l) => {
				lands = l;
				if (l.length) landId = l[0].id;
			})
			.catch((e) => (err = e.message));
	}
</script>

<svelte:head>
	<title>DAPE — Terbitkan Sertifikat Mutu</title>
</svelte:head>

<h1>Terbitkan Sertifikat Mutu</h1>
<p class="muted">
	Menggabungkan bukti sterilisasi 52 °C, uji Archimedes, grade, dan kepatuhan
	GDD menjadi payload yang ditandatangani <strong>Ed25519 di browser</strong>,
	lalu dirender sebagai QR. Verifikasi tetap sah meski tanpa internet.
</p>

{#if err}<p class="error">{err}</p>{/if}

<div class="grid" style="margin-top:1rem">
	<div class="card">
		<h2>Data Sertifikat</h2>
		<label class="muted" for="li">Lahan</label>
		<select id="li" bind:value={landId}>
			{#each lands as l}<option value={l.id}>{l.name}</option>{/each}
		</select>
		<div style="display:flex;gap:0.5rem;margin-top:0.5rem">
			<div><label class="muted" for="gd">GDD</label><input id="gd" type="number" bind:value={gdd} /></div>
			<div><label class="muted" for="ce">KTK</label><input id="ce" type="number" bind:value={cecMeq} /></div>
		</div>
		<div style="display:flex;gap:0.5rem;margin-top:0.5rem">
			<div><label class="muted" for="sc">Sterilisasi (°C)</label><input id="sc" type="number" bind:value={sterilizationC} /></div>
			<div><label class="muted" for="sr">Sink ratio</label><input id="sr" type="number" bind:value={sinkRatio} step="0.01" /></div>
		</div>
		<label class="muted" for="gr" style="display:block;margin-top:0.5rem">Grade</label>
		<select id="gr" bind:value={grade}>
			<option value="A+">A+</option><option value="A">A</option>
			<option value="B">B</option><option value="BS">BS</option>
		</select>
		<label style="display:block;margin-top:0.5rem">
			<input type="checkbox" bind:checked={fortificationCompliant} />
			<span class="muted">Patuh Fortification Lock (Ca+B+Si)</span>
		</label>
		<button class="btn" style="margin-top:0.75rem" onclick={issue}>🔏 Tandatangani &amp; Buat QR</button>
	</div>

	<div class="card">
		<h2>QR Sertifikat</h2>
		{#if qrDataUrl}
			<img src={qrDataUrl} alt="QR sertifikat" style="width:100%;max-width:320px" />
			<button class="btn secondary" style="margin-top:0.5rem" onclick={copyJSON}>
				{copied ? '✓ Tersalin' : 'Salin JSON sertifikat'}
			</button>
			<p class="muted" style="margin-top:0.5rem">
				Pindai di halaman <a href="/verify">Verifikasi QR</a> (atau portal pembeli)
				untuk memvalidasi tanda tangan Ed25519.
			</p>
		{:else}
			<p class="muted">Belum ada sertifikat. Isi data lalu klik tanda tangani.</p>
		{/if}
	</div>
</div>

{#if cert}
	<div class="card" style="margin-top:1rem">
		<h2>Payload Ditandatangani</h2>
		<table>
			<tbody>
				{#each Object.entries(cert.payload) as [k, v]}
					<tr><td><code>{k}</code></td><td>{JSON.stringify(v)}</td></tr>
				{/each}
			</tbody>
		</table>
		<p class="muted" style="margin-top:0.5rem">
			Public key (base64): <code>{cert.public_key}</code>
		</p>
	</div>
{/if}
