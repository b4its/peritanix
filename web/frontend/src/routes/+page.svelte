<script lang="ts">
	import { onMount } from 'svelte';
	import { health, listLands, API_BASE, type Land } from '$lib/api';
	import { db, flushQueue } from '$lib/db';
	import Icon from '$lib/Icon.svelte';

	let status = $state<'loading' | 'ok' | 'down'>('loading');
	let mlOK = $state(false);
	let lands = $state<Land[]>([]);
	let queue = $state(0);
	let err = $state('');

	async function load() {
		try {
			const h = await health();
			status = h.status === 'ok' ? 'ok' : 'down';
			mlOK = h.ml;
			lands = await listLands();
		} catch (e: any) {
			status = 'down';
			err = e.message;
		}
		queue = await db.syncQueue.count();
	}

	async function sync() {
		try {
			const r = await flushQueue();
			queue = await db.syncQueue.count();
			alert(`Sinkronisasi: ${r.applied} terkirim, ${r.duplicate} duplikat, ${r.failed} gagal`);
		} catch (e: any) {
			alert('Sinkronisasi gagal: ' + e.message);
		}
	}

	onMount(load);
</script>

<svelte:head>
	<title>DAPE — Dashboard</title>
</svelte:head>

<h1>Control Center</h1>
<p class="muted">
	Deterministic Agro-Physics Engine untuk budidaya cabai presisi — offline-first,
	Edge AI, sertifikasi kriptografis.
</p>

<div class="grid" style="margin-top:1.25rem">
	<div class="card">
		<h2><Icon name="pulse" size={16} /> Status Sistem</h2>
		{#if status === 'loading'}
			<p class="muted">Memeriksa…</p>
		{:else if status === 'ok'}
			<p class="row">
				<span class="badge high"><Icon name="check" size={12} /> API OK</span>
				<span class="badge" class:high={mlOK} class:medium={!mlOK}>
					<Icon name={mlOK ? 'check' : 'warning'} size={12} />
					ML {mlOK ? 'aktif' : 'mati'}
				</span>
			</p>
		{:else}
			<p><span class="badge low"><Icon name="cancel" size={12} /> API DOWN</span></p>
			<p class="error">{err}</p>
			<p class="muted">Endpoint: {API_BASE}</p>
		{/if}
		<button class="btn secondary" onclick={load}>
			<Icon name="refresh" size={14} /> Refresh
		</button>
	</div>

	<div class="card">
		<h2><Icon name="sync" size={16} /> Sinkronisasi Offline</h2>
		<p>Antrian tertunda: <strong>{queue}</strong> operasi</p>
		<button class="btn" onclick={sync} disabled={queue === 0}>
			<Icon name="upload" size={14} /> Kirim Delta Sync
		</button>
	</div>

	<div class="card">
		<h2><Icon name="pin" size={16} /> Ringkasan Lahan</h2>
		<p>Total lahan: <strong>{lands.length}</strong></p>
		<p class="muted">
			Total luas:{' '}
			{lands.reduce((s, l) => s + (l.area_ha ?? 0), 0).toFixed(3)} ha
		</p>
	</div>
</div>

<div class="card" style="margin-top:1rem">
	<h2><Icon name="leaf" size={16} /> Daftar Lahan</h2>
	{#if lands.length === 0}
		<p class="muted">Belum ada lahan. Jalankan <code>make web-seed</code> atau inisialisasi lahan.</p>
	{:else}
		<table>
			<thead>
				<tr><th>Nama</th><th>Luas (ha)</th><th>Elevasi (m)</th><th>GDD</th><th>Fase</th><th></th></tr>
			</thead>
			<tbody>
				{#each lands as l}
					<tr>
						<td>{l.name}</td>
						<td>{(l.area_ha ?? 0).toFixed(3)}</td>
						<td>{(l.elevation_m ?? 0).toFixed(0)}</td>
						<td>{(l.accumulated_gdd ?? 0).toFixed(0)}</td>
						<td><span class="badge accent">{l.current_phase}</span></td>
						<td>
							<a class="btn secondary" href={`/lands/${l.id}`}>
								Detail <Icon name="chevron-right" size={13} />
							</a>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	{/if}
</div>
