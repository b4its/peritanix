<script lang="ts">
	import { browser } from '$app/environment';
	import { modelManifest, modelList, API_BASE, type ModelCard } from '$lib/api';
	import Icon from '$lib/Icon.svelte';

	// The client's last-known model versions (persisted locally, like a real
	// device would after downloading).
	let known: Record<string, string> = {};
	let manifest = $state<ModelCard[]>([]);
	let registryVersion = $state('');
	let updateCount = $state(0);
	let all = $state<ModelCard[]>([]);
	let err = $state('');

	async function check() {
		err = '';
		try {
			const stored = localStorage.getItem('dape.models');
			known = stored ? JSON.parse(stored) : {};
			const m = await modelManifest(known);
			manifest = m.models;
			registryVersion = m.registry_version;
			updateCount = m.update_count;
			all = (await modelList()).models;
		} catch (e: any) {
			err = e.message;
		}
	}

	/** Simulate downloading + activating an updated model on-device. */
	function activate(name: string, version: string) {
		known[name] = version;
		localStorage.setItem('dape.models', JSON.stringify(known));
		check();
	}

	if (browser) check();
</script>

<svelte:head>
	<title>DAPE — Model &amp; OTA</title>
</svelte:head>

<h1>Model Edge &amp; OTA (Continuous Learning)</h1>
<p class="muted">
	Perangkat hanya mengunduh model yang <strong>versinya berubah</strong>
	(minim data). Bila tidak ada model terlatih, sistem memakai rule engine
	deterministik — jadi tetap berfungsi penuh.
</p>

{#if err}<p class="error">{err}</p>{/if}

<div class="card" style="margin-top:1rem">
	<h2>Status Registry</h2>
	<p>Versi registry: <code>{registryVersion || '—'}</code></p>
	<p>Pembaruan tersedia: <strong>{updateCount}</strong></p>
	<button class="btn" onclick={check}><Icon name="refresh" size={14} /> Periksa Pembaruan</button>
</div>

<div class="card" style="margin-top:1rem">
	<h2>Manifest untuk Klien Ini</h2>
	{#if manifest.length === 0}
		<p class="muted">Klik "Periksa Pembaruan".</p>
	{:else}
		<table>
			<thead>
				<tr><th>Model</th><th>Versi</th><th>Kuantisasi</th><th>Ukuran</th><th>Status</th><th></th></tr>
			</thead>
			<tbody>
				{#each manifest as m}
					<tr>
						<td>{m.name}</td>
						<td>{m.version}</td>
						<td>{m.quantisation}</td>
						<td>{m.size_mb.toFixed(1)} MB</td>
						<td>
							{#if m.update_available}
								<span class="badge medium">Update tersedia</span>
							{:else}
								<span class="badge high">Terbaru</span>
							{/if}
						</td>
						<td>
							{#if m.update_available}
								<button class="btn" onclick={() => activate(m.name, m.version)}>Unduh &amp; Aktifkan</button>
							{/if}
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	{/if}
</div>

<div class="card" style="margin-top:1rem">
	<h2>Semua Model Terdaftar</h2>
	<table>
		<thead><tr><th>Model</th><th>Versi</th><th>Tujuan</th></tr></thead>
		<tbody>
			{#each all as m}
				<tr><td>{m.name}</td><td>{m.version}</td><td class="muted">{m.purpose}</td></tr>
			{/each}
		</tbody>
	</table>
	<p class="muted" style="margin-top:0.5rem">API: <code>{API_BASE}</code></p>
</div>
