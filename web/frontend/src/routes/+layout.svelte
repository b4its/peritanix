<script lang="ts">
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import { page } from '$app/stores';
	import Icon from '$lib/Icon.svelte';
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

	const nav = [
		{ href: '/dashboard', label: 'Dashboard' },
		{ href: '/lands/new', label: 'Lahan' },
		{ href: '/diagnose', label: 'Diagnosis' },
		{ href: '/fertilize', label: 'Pupuk' },
		{ href: '/fertilizer', label: 'Dosis' },
		{ href: '/harvest', label: 'Panen' },
		{ href: '/certify', label: 'Sertifikat' },
		{ href: '/verify', label: 'Verifikasi' },
		{ href: '/economics', label: 'Ekonomi' },
		{ href: '/models', label: 'Model' },
		{ href: '/calibrate', label: 'Kalibrasi' },
		{ href: '/account', label: 'Akun' }
	];
	let path = $derived($page.url.pathname);
</script>

<div class="app">
	<header>
		<a href="/" class="brand" aria-label="DAPE beranda">
			<Icon name="chili" size={20} />
			<span>DAPE</span>
		</a>
		<nav>
			{#each nav as item}
				<a
					href={item.href}
					class:active={path === item.href || path.startsWith(item.href + '/')}
				>
					{item.label}
				</a>
			{/each}
		</nav>
		<span class="net" class:on={online} class:off={!online}>
			<Icon name="dot" size={10} />
			{online ? 'Online' : 'Offline'}
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
		font-family: ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
		background: var(--bg);
		color: var(--ink);
		-webkit-font-smoothing: antialiased;
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
		padding: 0.7rem 1.5rem;
		background: var(--surface);
		border-bottom: 1px solid var(--line);
		position: sticky;
		top: 0;
		z-index: 20;
	}
	.brand {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		font-weight: 700;
		font-size: 1.1rem;
		letter-spacing: -0.01em;
		color: var(--accent);
		text-decoration: none;
	}
	nav {
		display: flex;
		flex-wrap: wrap;
		gap: 0.15rem;
		flex: 1;
	}
	nav a {
		color: var(--ink-2);
		text-decoration: none;
		font-size: 0.88rem;
		font-weight: 500;
		padding: 0.35rem 0.6rem;
		border-radius: var(--radius-sm);
		transition: color 0.15s ease, background 0.15s ease;
	}
	nav a:hover {
		color: var(--orange-700);
		background: var(--orange-50);
	}
	nav a.active {
		color: var(--orange-700);
		background: var(--accent-weak);
		font-weight: 600;
	}
	.net {
		display: inline-flex;
		align-items: center;
		gap: 0.35rem;
		font-size: 0.82rem;
		font-weight: 600;
		padding: 0.3rem 0.65rem;
		border-radius: 999px;
		border: 1px solid var(--line);
		white-space: nowrap;
	}
	.net.on {
		color: var(--ok);
		border-color: var(--ok-bg);
		background: var(--ok-bg);
	}
	.net.off {
		color: var(--danger);
		border-color: var(--danger-bg);
		background: var(--danger-bg);
	}
	main {
		flex: 1;
		padding: var(--space);
		max-width: 1200px;
		width: 100%;
		margin: 0 auto;
	}
	footer {
		padding: 1.25rem 1.5rem;
		text-align: center;
		font-size: 0.8rem;
		color: var(--muted);
		border-top: 1px solid var(--line);
	}
	@media (max-width: 720px) {
		header {
			flex-wrap: wrap;
			gap: 0.75rem;
		}
		nav {
			order: 3;
			width: 100%;
		}
	}
</style>
