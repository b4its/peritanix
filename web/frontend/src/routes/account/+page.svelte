<script lang="ts">
	import { register, login, type AuthResult } from '$lib/api';
	import { auth } from '$lib/auth';

	let mode = $state<'login' | 'register'>('login');
	let name = $state('');
	let phone = $state('');
	let password = $state('');
	let role = $state('farmer');
	let err = $state('');
	let ok = $state('');
	let busy = $state(false);

	async function submit() {
		err = '';
		ok = '';
		busy = true;
		try {
			let res: AuthResult;
			if (mode === 'register') {
				if (!name.trim()) throw new Error('Nama wajib diisi.');
				res = await register({ name, phone, role, password });
			} else {
				res = await login(phone, password);
			}
			auth.setSession(res.token, res.user);
			ok = `Berhasil masuk sebagai ${res.user.name} (${res.user.role}).`;
		} catch (e: any) {
			err = e.message;
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head>
	<title>DAPE — Akun</title>
</svelte:head>

<h1>Akun</h1>
<p class="muted">
	Masuk atau buat akun. Token JWT disimpan lokal dan dilampirkan otomatis ke
	permintaan API. Akun diperlukan saat <code>AUTH_REQUIRED=true</code> di server.
</p>

<div class="card" style="max-width:420px;margin-top:1rem">
	<div style="display:flex;gap:0.5rem;margin-bottom:0.75rem">
		<button class="btn {mode === 'login' ? '' : 'secondary'}" onclick={() => (mode = 'login')}>Masuk</button>
		<button class="btn {mode === 'register' ? '' : 'secondary'}" onclick={() => (mode = 'register')}>Daftar</button>
	</div>

	{#if mode === 'register'}
		<label class="muted" for="nm">Nama</label>
		<input id="nm" bind:value={name} placeholder="Pak Budi" />
		<label class="muted" for="rl" style="display:block;margin-top:0.5rem">Peran</label>
		<select id="rl" bind:value={role}>
			<option value="farmer">Petani</option>
			<option value="worker">Pekerja</option>
			<option value="agronomist">Agronomis</option>
			<option value="koperasi">Koperasi</option>
			<option value="offtaker">Off-taker</option>
		</select>
	{/if}

	<label class="muted" for="ph" style="display:block;margin-top:0.5rem">Nomor HP</label>
	<input id="ph" bind:value={phone} placeholder="62812..." />
	<label class="muted" for="pw" style="display:block;margin-top:0.5rem">Kata sandi</label>
	<input id="pw" type="password" bind:value={password} />

	<button class="btn" style="margin-top:0.75rem" onclick={submit} disabled={busy}>
		{busy ? 'Memproses…' : mode === 'register' ? 'Daftar' : 'Masuk'}
	</button>

	{#if err}<p class="error" style="margin-top:0.5rem">{err}</p>{/if}
	{#if ok}<p style="color:#16a34a;margin-top:0.5rem">{ok}</p>{/if}
</div>
