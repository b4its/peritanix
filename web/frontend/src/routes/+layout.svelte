<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import '../app.css';

	let { children } = $props();
	let online = $state(true);
	let queueCount = $state(0);

	onMount(() => {
		if (!browser) return;
		// Stable device id for idempotency keys.
		if (!localStorage.getItem('dape.device_id')) {
			localStorage.setItem('dape.device_id', 'web-' + crypto.randomUUID().slice(0, 8));
		}
		const update = () => (online = navigator.onLine);
		update();
		window.addEventListener('online', update);
		window.addEventListener('offline', update);
		return () => {
			window.removeEventListener('online', update);
			window.removeEventListener('offline', update);
		};
	});
</script>

<div class="app">
	<header>
		<a href="/" class="brand">🌶️ DAPE</a>
		<nav>
			<a href="/dashboard">Dashboard</a>
			<a href="/lands/new">+ Lahan</a>
			<a href="/diagnose">Diagnosis</a>
			<a href="/fertilize">Pupuk</a>
			<a href="/harvest">Panen</a>
			<a href="/certify">Sertifikat</a>
			<a href="/verify">Verifikasi</a>
			<a href="/economics">Ekonomi</a>
			<a href="/models">Model</a>
			<a href="/account">Akun</a>
		</nav>
		<span class="net" class:on={online} class:off={!online}>
			{online ? '● Online' : '○ Offline'}
		</span>
	</header>
	<main>
		{@render children()}
	</main>
	<footer>
		Deterministic Agro-Physics Engine — Precision Micro-Farming Cabai
	</footer>
</div>

<style>
	:global(body) {
		margin: 0;
		font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
		background: #f6f7f4;
		color: #1c2520;
	}
	.app {
		min-height: 100vh;
		display: flex;
		flex-direction: column;
	}
	header {
		display: flex;
		align-items: center;
		gap: 1.5rem;
		padding: 0.75rem 1.5rem;
		background: #14261c;
		color: #eafaf0;
	}
	.brand {
		font-weight: 700;
		font-size: 1.15rem;
		color: #7ff0a8;
		text-decoration: none;
	}
	nav {
		display: flex;
		gap: 1rem;
		flex: 1;
	}
	nav a {
		color: #c9e8d4;
		text-decoration: none;
		font-size: 0.95rem;
	}
	nav a:hover {
		color: #7ff0a8;
	}
	.net {
		font-size: 0.85rem;
		font-weight: 600;
	}
	.net.on {
		color: #7ff0a8;
	}
	.net.off {
		color: #ffb4a2;
	}
	main {
		flex: 1;
		padding: 1.5rem;
		max-width: 1200px;
		width: 100%;
		margin: 0 auto;
		box-sizing: border-box;
	}
	footer {
		padding: 1rem 1.5rem;
		text-align: center;
		font-size: 0.8rem;
		color: #6b7a72;
	}
</style>
